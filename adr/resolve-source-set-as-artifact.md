Source sets are deliberately considered separate artifacts in terms of Maven
dependency resolution. This way, we can utilize Maven Dependency Resolver
library with any count of project source sets (at least we are not forced to
build our own dependency resolution mechanism) while mitigating unclear
resolution rules regarding custom test source sets, e.g. integration tests.

## Status update: source sets are project-internal only

`SourceSet` is no longer part of the public dependency API. It was removed from
`MainSourceSetDependency`, `TestSourceSetDependency` and
`ExtraSourceSetDependency`, so a `SourceSet` can only be referenced from within
the project that declares it (the `main`/`test`/extra source sets of the same
`Project`). Depending on another project's source set is not allowed.

When a source set of one project has to be consumed by another project (e.g. the
`core`/`web`/`oauth2-core`/`oauth2-jose` test source sets in
`build-spring-security`), that source set is built as a standalone `Project`
whose main source set holds those sources. `BuildSpringSecurity` creates such
`spring-security-<module>-tests` projects, sharing the owning module's directory
but using a distinct `ArtifactLayout` root (`build-system-tests`) so their
outputs do not collide. Consumers then depend on the test project like on any
other project.

The underlying Maven representation is unchanged: a same-project non-main source
set is still resolved as a `-source-set-<id>` artifact (`Dependency.OnSourceSet`),
which is what lets the extra-source-set mechanism (e.g. `testFixtures`) work.
