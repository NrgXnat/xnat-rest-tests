# j2k_snapshots

One study, two series of three instances each, for `TestSnapshotCodecs`: an uncompressed control
(`1.2.840.10008.1.2.1`) and JPEG 2000 Lossless (`1.2.840.10008.1.2.4.90`). They differ in transfer
syntax and nothing else, which is what makes the first a control for the second.

This exists because the suite has no JPEG 2000 data. Of the test data sets available, exactly one is
compressed at all — `TestData.JPEGLOSSLESS_2000`, which is JPEG Lossless SV1 — and everything else is
Implicit or Explicit VR Little Endian. That gap matters because `dcm4che-imageio-opencv` registers a
separate ImageIO service provider per codec family: `NativeJPEGImageReaderSpi` serves `jpeg-cv`,
`NativeJ2kImageReaderSpi` serves `jpeg2000-cv`, `NativeJLSImageReaderSpi` serves `jpeg-ls-cv`. JPEG
Lossless resolving tells you nothing about whether JPEG 2000 resolves, and `jpeg2000-cv` is the one
that failed in production (XNAT-6581, XNAT-6743).

The uncompressed control is generated here rather than reused from an existing data set so that both
series arrive in one import, share a study, and differ only in transfer syntax. Reusing
`TestData.JPEGLOSSLESS_2000` for a third, JPEG-Lossless case was considered and left out: it is a
separate session whose archived label the test would have to discover, and the failure it would catch
-- the OpenCV native not loading at all -- is already caught by the JPEG 2000 case.

256x256 MONOCHROME2, subject `J2K_TEST_001`, session `J2K_SNAPSHOTS` via `PatientComments`, which is
what XNAT's default identifier reads. Pixels are computed from a formula — a diagonal gradient,
concentric rings, and a bright bar whose row tracks the instance — so a snapshot that renders wrongly
or not at all is obvious by eye. **There is no patient data here**; nothing derives from a real study.

Regenerate with dcm4che plus `dcm4che-imageio-opencv` and the matching OpenCV native on
`java.library.path`, by transcoding uncompressed instances to `UID.JPEG2000Lossless`. See
`docs/mirroring-opencv-codecs.md` in the xnat repository for where those artifacts come from.
