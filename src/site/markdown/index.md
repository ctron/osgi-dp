# Introduction

The OSGi DP Maven plugin created an OSGi deployment package based on the configuration
of an Eclipse Tycho™ feature project, bundle project or any other Maven Java project.

The plugins goal `build` will, in addition to the feature which Tycho provides, create a DP archive
containing all directly included bundles. This includes bundles included by included features (no kidding).

Features may reference dependencies either as "included" or "required". Dependencies which are "included" become
part of that feature. This is honored by the OSGi DP plugin. It will traverse through all dependencies and only
include dependencies which are marked as "included". If the dependency is a feature and it is "included", then its
features and bundles will be considered as well.

In addition is it possible to specify a list of "additional dependencies" which will be included.

## Contributing

All contributions are welcome!
  
## See also

 * [Eclipse Tycho™](https://eclipse.org/tycho) 
