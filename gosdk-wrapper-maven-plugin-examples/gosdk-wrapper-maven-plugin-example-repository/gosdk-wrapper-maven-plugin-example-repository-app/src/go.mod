module github.com/raydac/gosdk-wrapper-maven-plugin/repository/app

require (
        github.com/raydac/gosdk-wrapper-maven-plugin/repository/lib/mathutil v1.0.0
)

replace (
        github.com/raydac/gosdk-wrapper-maven-plugin/repository/lib/mathutil => ./mathutil
)