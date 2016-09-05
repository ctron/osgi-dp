# Qualified version
 
In some situations the plugin needs a qualified OSGi version. The plugin will
first use the explicitly provided version. If this is not set then the plugin
will generate a qualified version. First it will re-use a qualified OSGi
version from Tycho if the project is built using Tycho. Otherwise the
project's version is used and the `-SNAPSHOT` suffix will be
removed by the current timestamp. This also means that non
`-SNAPSHOT` versions have to be OSGi compliant.

