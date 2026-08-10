# RPKit

RPKit is a suite of plugins for Minecraft roleplay servers. It includes standard libraries, which allow easy interfacing with a specific subset of functionality, and reference implementations of these.

This repository is [Dans-Plugins](https://github.com/Dans-Plugins)' fork of [RP-Kit/RPKit](https://github.com/RP-Kit/RPKit).

## Quick Links

- [Website](http://rpkit.com/) (upstream project)
- [Wiki](https://github.com/RP-Kit/RPKit/wiki) (upstream project)
- [Discord](https://discord.gg/C9br2MdPcc) (upstream project)
- [Upstream repository](https://github.com/RP-Kit/RPKit)

## Table of Contents

- [Support](#support)
- [Installation](#installation)
- [Compiling the Project](#compiling-the-project)
- [Contributing](#contributing)
- [Authors and Acknowledgements](#authors-and-acknowledgements)
- [License](#license)

## Support

For general questions about RPKit, the upstream project's [Discord server](https://discord.gg/C9br2MdPcc) is the place to ask.

For a bug or feature request in *this fork*, please open a [GitHub issue](https://github.com/Dans-Plugins/RPKit/issues/new). Issues concerning upstream RPKit itself belong on the [upstream tracker](https://github.com/RP-Kit/RPKit/issues/new).

## Installation

RPKit is not a single plugin. Each module is built and installed as its own JAR, and modules come in two kinds:

- `rpk-<area>-lib-bukkit` — the API for an area of functionality.
- `rpk-<area>-bukkit` — the reference implementation of that API.

Every module declares the modules it needs under `depend:` in its own `plugin.yml`. For example, `bukkit/rpk-players-bukkit/src/main/resources/plugin.yml` depends on `rpk-core-bukkit` and `rpk-player-lib-bukkit`. `rpk-core-bukkit` is required by everything.

1. Obtain the module JARs, either by building them from source (see [Compiling the Project](#compiling-the-project)) or by downloading them from the upstream [releases page](https://github.com/RP-Kit/RPKit/releases). Each module is a separate download; if you are not sure which ones you need, take all of them.
2. Place the JAR files in the `plugins` directory of your server.
3. Start your server. Each plugin creates its own data folder named after itself — for example `plugins/rpk-players-bukkit/` — and writes its default configuration there. Modules that use a database also write a `database.yml` into that folder, defaulting to SQLite (`jdbc:sqlite:rpkit_players.db`); `MYSQL` is the other supported dialect.
4. Edit the generated `config.yml` and `database.yml` files to taste.
5. Restart your server.

## Compiling the Project

**JDK 17 is required.** The build pins Gradle 7.6, Kotlin 1.7.22 and `jvmTarget = "17"`; JDK 21 fails with `Unsupported class file major version 65`, and JDK 11 fails to resolve the build's plugins. Continuous integration builds this project on Temurin 17.

To compile the project, run the following command in the root directory of the project.

Windows:

```cmd
.\gradlew.bat clean build
```

Linux:

```shell
./gradlew clean build
```

This compiles all 71 modules listed in `settings.gradle` and writes each module's JARs to that module's `build/libs` directory. The Bukkit modules live under `bukkit/`, so `rpk-chat-bukkit`'s output lands in `bukkit/rpk-chat-bukkit/build/libs`. Each Bukkit module produces both a plain JAR and a shaded `-all.jar`; the shaded one is what belongs in your server's `plugins` directory.

## Contributing

To contribute to this fork, please [fork](https://github.com/Dans-Plugins/RPKit/fork) the repository and submit a pull request against `main`. Please ensure that your code is well-tested and follows the existing code style — in particular, every source file carries the Apache-2.0 licence header, and every command permission node is declared in its module's `plugin.yml`.

Contributions intended for RPKit itself, rather than for this fork, should be sent to the [upstream repository](https://github.com/RP-Kit/RPKit).

## Authors and Acknowledgements

### Developers

| Name | Contributions |
| ---- | ------------- |
| Ren Binden (`alyphen`) | Creator of RPKit |
| [Dans-Plugins](https://github.com/Dans-Plugins) | Maintainer of this fork |

Thanks are owed to everyone who has [contributed to upstream RPKit](https://github.com/RP-Kit/RPKit/graphs/contributors).

## License

RPKit is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for more information.
