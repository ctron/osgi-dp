# Usage

The following sections show how the OSGi DP packager can be used.
  
## Common

Add the following execution to project in order to build a DP:

    <plugins>
        …
        <plugin>
            <groupId>de.dentrassi.maven</groupId>
            <artifactId>osgi-dp</artifactId>
            <executions>
                <execution>
                    <goals>
                        <goal>build</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        …
    </plugins>
  
## Tycho Feature

Adding the `build` goal to an `eclipse-feature` type project will create a DP based on the directly
included plugins of a feature. It will traverse all included features for bundle lookup as well.

Referenced bundles and features will be ignored.

## Tycho Plugin

Adding the `build` goal to an `eclipse-plugin` type project will create a DP based on the current bundle.
No dependencies will be added, unless they are adding using the "additional dependencies" feature. 

## Maven Bundle Plugin

Adding the `build` goal to an `bundle` type project (maven-bundle-plugin) will create a DP like using the `eclipse-plugin` target. Only it is not possible to use a qualified filename as output.