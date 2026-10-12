#!/bin/sh
# Run once from the project root, after unzipping: removes the Odysee files and the helper classes
# that were only added for Odysee. Then it deletes itself.
rm -rf common/src/main/java/com/liskovsoft/smartyoutubetv2/common/providers/odysee
rm -f common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/ProviderHeadersDataSourceFactory.java
rm -f common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/errors/OdyseeLoadErrorHandlingPolicy.java
rm -- "$0"
echo "Odysee files removed."
