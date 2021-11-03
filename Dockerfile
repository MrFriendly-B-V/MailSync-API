FROM rust:1.56.1-slim-buster as API_BUILDER
RUN apt update && apt install -y \
    musl-tools \
    pkgconf
RUN rustup target add x86_64-unknown-linux-musl

WORKDIR /usr/src/
COPY ./src /usr/src/src/
COPY ./migrations /usr/src/migrations
COPY ./Cargo.toml /usr/src

ENV RUSTFLAGS='-C link-arg=-s'
RUN cargo build --release --target x86_64-unknown-linux-musl

FROM node:16-bullseye-slim as FRONTEND_BUILDER
RUN apt update && apt install -y \
    make

WORKDIR /usr/src
COPY ./frontend /usr/src/

RUN make clean
RUN make dist

# Runtime image
FROM alpine:latest
RUN apk add --no-cache ca-certificates ffmpeg
COPY --from=API_BUILDER /usr/src/target/x86_64-unknown-linux-musl/release/mailsync /usr/local/bin/mailsync
COPY --from=FRONTEND_BUILDER /usr/src/dist /usr/local/bin/frontend_dist/
COPY ./log4rs.yaml /usr/local/bin/

RUN chmod a+rx /usr/local/bin/*
RUN adduser mailsync -s /bin/false -D -H
USER mailsync

ENV DOCKERIZED=TRUE

EXPOSE 8080
WORKDIR /usr/local/bin
ENTRYPOINT [ "/usr/local/bin/mailsync" ]