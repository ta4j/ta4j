# Linux CUDA continuation handoff

Windows completion freezes CUDA operation ABI 1, RNG version 1, JNI primitive
payload ordering, native status meanings, golden fixtures, FP64 tolerance
(`1e-4`), and the shared kernel source under
`ta4j-accelerator/src/main/native/cuda/`. Linux must reuse those contracts; it
must not create a second kernel or platform-specific RNG corpus.

## Work remaining on a Linux CUDA host

1. Generalize the CMake platform guard and host/JNI linker branches while
   preserving the CUDA source and `sm_120`/`compute_120` device-code policy.
2. Produce `libta4j-cuda-accelerator.so` and attach a
   `cuda-linux-x86_64` classifier under
   `META-INF/native/linux-x86_64/` with a SHA-256 sidecar.
3. Add Linux x86_64 loader selection, atomic extraction plus execute
   permissions, and bounded diagnostics/tests for `ldd`, rpath, architecture,
   unwritable cache, missing runtime, and checksum failures.
4. Run the unchanged Java fixture, integration, failure, and concurrency matrix
   plus all four Compute Sanitizer modes. Add Linux-only driver-reset, signal,
   container, memory-pressure, and loader cases.
5. Run at least five fresh JVMs per workload and emit the frozen report schema
   with distro, kernel, glibc, libstdc++, JDK, GCC, CMake, driver, toolkit, GPU,
   compute capability, container/runtime, commit, tree, and native checksum.

Linux support may be advertised only for the exact green environment matrix.
Failure to qualify Linux does not invalidate the Windows classifier and must
not broaden its claim. Start on the Linux host with:

```bash
scripts/acceleration/linux-cuda-handoff.sh .
```
