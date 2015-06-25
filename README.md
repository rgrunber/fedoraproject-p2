# fedoraproject-p2
p2 repository implementation for OSGi resources on Fedora

The project depends on exact version of XMvn (https://mizdebsk.fedorapeople.org/xmvn), currently 2.4.0, which has to be installed in the local maven repository so a regular maven bulid can find it. Please fetch a tarball or git clone from XMvn site and build and install it prior to trying to build fedoraproject-p2.

Alternatively, if on Fedora, one can try building with xmvn itself `xmvn verify` if willing to use the system installed XMvn version and ignoring potential version differences.
