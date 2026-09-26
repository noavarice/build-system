Source sets are deliberately considered separate artifacts in terms of Maven
dependency resolution. This way, we can utilize Maven Dependency Resolver
library with any count of project source sets (at least we are not forced to
build our own dependency resolution mechanism) while mitigating unclear
resolution rules regarding custom test source sets, e.g. integration tests.
