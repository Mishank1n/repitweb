# Временный HTTPS-адрес для Mini App через localhost.run: обычный SSH, без регистрации.
FROM alpine:3.22
RUN apk add --no-cache openssh-client \
    && mkdir /tunnel && chown nobody:nobody /tunnel
COPY run.sh /run.sh
USER nobody
ENV HOME=/tmp
ENTRYPOINT ["/bin/sh", "/run.sh"]
