# fedoraproject-p2
p2 repository implementation for OSGi resources on Fedora

The project depends on exact version of XMvn (https://mizdebsk.fedorapeople.org/xmvn), currently 2.5.0, which has to be installed in the local maven repository so a regular maven bulid can find it. Please fetch a tarball or git clone from XMvn site and build and install it prior to trying to build fedoraproject-p2.

Alternatively, if on Fedora, one can try building with xmvn itself `xmvn verify` if willing to use the system installed XMvn version and ignoring potential version differences.


## Areas of interest

FedoraBundleIndex, FedoraMetadataRepositoryFactory, FedoraArtifactRepositoryFactory, FedoraMetadataRepository, FedoraArtifactRepository

Classes responsible for validating, parsing, and presenting OSGi metadata on a filesystem to p2.


SCL, EclipseSystemLayout, FedoraBundleRepository, CompoundBundleRepository

Classes responsible for integrating with Fedora's OSGi bundle packaging layout. These make the distinction between platform/internal/external units as well as allowing for grouping repositories by software collections.


EclipseArtifactInstaller, DefaultEclipseInstaller

Classes responsible for driving the installation process. DefaultEclipseInstaller is generally where most of the dependency resolution, and provides/requires generation is done.

## Installable Unit Generation

**How do we generate installable units (IInstallableUnit) from bundles/features ?**

Initially fedoraproject-p2 had it's own implementation of IInstallableUnit called FedoraInstallableUnit that was very similar to the implementations used in p2. Later, it seemed like a better idea to use the p2 APIs for this (even if they were provisional, or internal) for the sake of not diverging.

To answer the question though :

`public static IInstallableUnit createBundleIU(IArtifactKey, File)` from org.eclipse.equinox.internal.p2.touchpoint.eclipse.PublisherUtil

The file is just the path to the bundle/feature we care about, so you might be wondering how we generate the IArtifactKey. The interface is simple enough that one might consider making their own implementation and filling in the necessary data from the bundle's manifest metadata (and this was done at one point), but there's also a method that performs this task :

`public static IArtifactKey createBundleArtifactKey(String, String)` from org.eclipse.equinox.p2.publisher.eclipse.BundlesAction

`public static IArtifactKey createFeatureArtifactKey(String, String)` from org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction

Where the 2 string arguments are the Bundle-SymbolicName and Bundle-Version respectively. With this information we can generate any installable unit needed. From there it's just a matter of satisfying the APIs required by IMetadataRepository, and IArtifactRepository.


## Classification of Installable Units

Some installable units are more special than others.

Platform units are what we use to refer to installable units that make up the Eclipse Platform.

*Examples : org.eclipse.help.base, org.eclipse.equinox.p2.metadata (but all the p2 units are basically part of the platform), org.eclipse.ui.*


Internal units are what we use to refer to installable units that are not part of the base platform, but that are Eclipse plugins and make functional contributions to the Eclipse workbench. Some might refer to these as 'dropin' units since one could make them available using the dropins/reconciler mechanism.

*Examples : org.eclipse.egit.core, org.eclipse.cdt.ui, org.eclipse.jdt.core*

External units are what we use to refer to installable units that are OSGi bundles, but that don't contribute functionality directly to the Eclipse workbench, except in that they're libraries. They could just as easily be used outside of Eclipse.

*Examples : org.apache.commons.io, org.tukaani.xz, org.sat4j.core*


## Hierarchy of Units

FedoraMetadataRepository - A set of units in a directory. This might represent a set of platform/internal/external units
FedoraBundleRepository : A grouping of platform, internal, and external units rooted at some location
CompoundBundleRepository : A grouping of units from these rooted locations (eg. software collections)

Example : One could have a CompoundBundleRepository that managed `/` and `/opt/rh/devtoolset-3/`, each of which would **generally** have platform units under usr/lib/eclipse, internal units under usr/share/eclipse/dropins, usr/lib/eclipse/dropins , and external units under usr/share/java, usr/lib/java  .

We say **generally** because org.sat4j.core, for example, is an external unit, but it can certainly be found inside the platform location (usr/lib/eclipse) since it is also a dependency of p2. It's easy enough to define that  platform/internal unit must not be present in a location reserved for external units.

