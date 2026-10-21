package ru.repitbot.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.repitbot.config.AppProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository users;
    private final Credentials credentials;
    private final AppProperties props;

    public UserService(UserRepository users, Credentials credentials, AppProperties props) {
        this.users = users;
        this.credentials = credentials;
        this.props = props;
    }

    /**
     * Можно ли присылать пользователю уведомления. Все рассылки (напоминания, ДЗ, отчёты)
     * обязаны проверять это: после отзыва согласия писать человеку нельзя.
     */
    public boolean canMessage(User user) {
        return user.getRole() == Role.TUTOR
                || (user.getConsentAt() != null && user.getDeleteRequestedAt() == null);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(long id) {
        return users.findById(id);
    }

    @Transactional(readOnly = true)
    public List<User> students() {
        return users.findByRoleOrderByIdAsc(Role.STUDENT);
    }

    @Transactional(readOnly = true)
    public Optional<User> findTutor() {
        return users.findFirstByRole(Role.TUTOR);
    }

    @Transactional(readOnly = true)
    public boolean tutorExists() {
        return users.existsByRole(Role.TUTOR);
    }

    /** Пользователь по логину и паролю; пусто — неверный логин или пароль (что именно, не говорим). */
    @Transactional(readOnly = true)
    public Optional<User> authenticate(String login, String password) {
        Optional<User> user = users.findByLogin(Credentials.normalizeLogin(login));
        boolean ok = credentials.matches(password, user.map(User::getPasswordHash).orElse(null));
        return ok ? user : Optional.empty();
    }

    /** Новый пользователь с проверенными логином, паролем и именем. Логин занят — понятная ошибка. */
    @Transactional
    public User create(Role role, String login, String password, String firstName, String lastName) {
        String normalized = Credentials.validLogin(Credentials.normalizeLogin(login));
        Credentials.validatePassword(password, normalized);
        if (users.existsByLogin(normalized)) {
            throw new IllegalArgumentException("Логин «" + normalized + "» уже занят — придумайте другой");
        }
        User user = new User(normalized, credentials.hash(password), role, props.timezone());
        user.rename(firstName, lastName);
        return users.save(user);
    }

    /** Аккаунт репетитора при первой настройке сайта. Второй репетитор не создаётся. */
    @Transactional
    public User createTutor(String login, String password, String firstName, String lastName) {
        if (users.existsByRole(Role.TUTOR)) {
            throw new IllegalStateException("Репетитор уже настроен");
        }
        return create(Role.TUTOR, login, password, firstName, lastName);
    }

    @Transactional
    public void setPassword(long userId, String password) {
        User user = users.findById(userId).orElseThrow();
        Credentials.validatePassword(password, user.getLogin());
        user.changePassword(credentials.hash(password));
    }

    /** Смена пароля из настроек: нужен текущий пароль. */
    @Transactional
    public void changePassword(long userId, String current, String password) {
        User user = users.findById(userId).orElseThrow();
        if (!credentials.matches(current, user.getPasswordHash())) {
            throw new IllegalArgumentException("Текущий пароль введён неверно");
        }
        setPassword(userId, password);
    }

    /** Запросы на удаление, которые ждут дольше заданного срока. */
    @Transactional(readOnly = true)
    public List<User> deletionRequestsOlderThan(Duration age) {
        return users.findByDeleteRequestedAtBeforeOrderByDeleteRequestedAtAsc(Instant.now().minus(age));
    }

    /** Отзыв согласия из настроек: дальше уведомлений нет, репетитор удаляет данные в течение 30 дней. */
    @Transactional
    public User requestDeletion(long userId) {
        User user = users.findById(userId).filter(u -> u.getRole() != Role.TUTOR).orElseThrow();
        user.requestDeletion();
        return user;
    }

    /**
     * Удаление по запросу: только если запрос ещё действует.
     * Если человек передумал и снова дал согласие, старая кнопка «Удалить» ничего не сделает.
     */
    @Transactional
    public Optional<User> deleteOnRequest(long userId) {
        Optional<User> user = users.findById(userId)
                .filter(u -> u.getRole() != Role.TUTOR && u.getDeleteRequestedAt() != null);
        user.ifPresent(users::delete);
        return user;
    }

    /** Полное удаление: членство в группах, связи с родителями и сессии удаляются каскадом в БД. */
    @Transactional
    public Optional<User> delete(long userId) {
        Optional<User> user = users.findById(userId).filter(u -> u.getRole() != Role.TUTOR);
        user.ifPresent(users::delete);
        return user;
    }
}
