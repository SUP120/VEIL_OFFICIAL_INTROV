FROM debian:bookworm-slim AS download
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && curl --fail --show-error --silent --location --retry 3 \
       https://github.com/godotengine/godot-builds/releases/download/4.5.2-stable/Godot_v4.5.2-stable_linux.x86_64.zip -o /tmp/godot.zip \
    && echo "87f6e6be292929e363d15ed9052f277b2ba4e95ed994e1e099048097be2dfd03  /tmp/godot.zip" | sha256sum -c - \
    && unzip -p /tmp/godot.zip Godot_v4.5.2-stable_linux.x86_64 > /godot && chmod 755 /godot

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates libstdc++6 libfontconfig1 libx11-6 libxcursor1 libxi6 libxinerama1 libxrandr2 libasound2 libgl1 \
    && rm -rf /var/lib/apt/lists/* && useradd --create-home --uid 10001 veil
COPY --from=download /godot /usr/local/bin/godot
RUN godot --headless --version
WORKDIR /home/veil
COPY veil_world.pck ./veil_world.pck
USER veil
ENV HOME=/home/veil VEIL_WORLD_BIND=0.0.0.0 PORT=10000
EXPOSE 10000
ENTRYPOINT ["godot","--headless","--max-fps","60","--main-pack","veil_world.pck","--","--online-server"]
