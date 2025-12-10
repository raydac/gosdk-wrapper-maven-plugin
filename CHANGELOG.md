# 1.1.3 (SNAPSHOT)

    -

# 1.1.2 (09-dec-2025)

    - fixed search of appropriate GoSDK site in default AUTO mode  [#11](https://github.com/raydac/gosdk-wrapper-maven-plugin/issues/11)
    - added `forceGoSdkFromPath` boolean flag to force search of installed GoSDK folder among folders listed in OS PATH environment variable [#10](https://github.com/raydac/gosdk-wrapper-maven-plugin/issues/10)

# 1.1.1 (30-nov-2025)

    - added `cache-sdk` mojo to ensure load GoSDK and to provide way to export paths to work folders through inject of maven project properties

# 1.1.0 (01-nov-2025)

    - Improved parsing of SDK list to support many formats and be prepared for load SDK through site instead of store [#7](https://github.com/raydac/gosdk-wrapper-maven-plugin/issues/7)  

# 1.0.5 (30-jun-2025)

    - updated dependencies and fixed vulnerable dependency alert

# 1.0.4 (29-mar-2025)

    - added way to load GoSDK from Maven repository as an artifact, parameter `sdkArtifactId`
    - refactoring and polishing

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
