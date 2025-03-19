# 1.0.4 (SNAPSHOT)

    -

# 1.0.3 (19-mar-2025)

    - improved examples
    - added default GOPATH if no any predefined (can be turned of with `mayAddInternalGOPATH`)
    - added `echo` and `echoWarn` for all mojos
    - added `path` into `execute` mojo
    - refactoring

# 1.0.2 (16-mar-2025)

    - added `give-all-permissions` mojo
    - fixed exception under Windows (#3)
    - improved debug logging
    - disabled mass tracing log messages from Apache Http 5 Client
    - fixed wrong std output log file destination

# 1.0.1 (12-mar-2025)

    - fixed stuck [#1](https://github.com/raydac/gosdk-wrapper-maven-plugin/issues/1)
    - added flag `hideLoadIndicator` to hide GoSDK loading bar from log
    - fixed wrong default value for `workDir`

# 1.0.0 (03-mar-2025)

    - initial version
