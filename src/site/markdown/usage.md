# Usage

The following sections show how the OSGi DP packager can be used.

## As primary output

Add the following plugin as an extension to you build:

    <plugins>
        …
        <plugin>
            <groupId>de.dentrassi.maven</groupId>
            <artifactId>osgi-dp</artifactId>
            <extensions>true</extensions>
        </plugin>
        …
    </plugins>
    
And then specify the packaging type for your Maven project:

	<packaging>dp</packaging>

This will skip the default Maven lifecycle and simply build the DP during the `package` phase. It will
attach the resulting DP as main artifact. In contrast to the "secondary output" variant of this bundle,
also the declared dependencies of this project will be included in the final package.   

## As secondary output

Add the following execution to project in order to build a DP:

	<plugins>
		…
			<plugin>
				<groupId>de.dentrassi.maven</groupId>
				<artifactId>osgi-dp</artifactId>
				<executions>
					<execution>
						<goals>
							<goal>bundle</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		…
	</plugins>

## As secondary output with Tycho

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
  
### Tycho Feature

Adding the `build` goal to an `eclipse-feature` type project will create a DP based on the directly
included plugins of a feature. It will traverse all included features for bundle lookup as well.

Referenced bundles and features will be ignored.

### Tycho Plugin

Adding the `build` goal to an `eclipse-plugin` type project will create a DP based on the current bundle.
No dependencies will be added, unless they are adding using the "additional dependencies" feature. 

