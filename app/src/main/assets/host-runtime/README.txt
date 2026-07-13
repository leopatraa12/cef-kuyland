Host runtime bundle for the launcher.

This folder is the package-ready place for an embedded ARM runtime.

Preferred bundle layout:
- server/omp-server
- server/samp-server
- server/components/*.so

Fallback flat asset runtime names:
- bin/xyron-host-arm
- bin/omp-server-arm
- bin/samp03svr-arm
- bin/omp-server
- bin/samp-server
- bin/samp03svr

Accepted packaged native runtime names:
- jniLibs/<abi>/libxyron_host.so
- jniLibs/<abi>/libomp_server_arm.so
- jniLibs/<abi>/libsamp03svr_arm.so

The launcher will try the packaged native runtime first.
If none exists, it will copy files from assets/host-runtime to Downloads/XyronHost,
preserving the folder structure.
