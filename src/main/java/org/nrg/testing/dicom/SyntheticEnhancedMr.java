package org.nrg.testing.dicom;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.dicom.transform.DicomFileWriter;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Large synthetic Enhanced MR objects for a {@link LocallyCacheableDicomTransformation} with no base data:
 * {@link #headers} generates the datasets without their pixel data, and {@link PixelDataWriter} writes each one and then
 * streams generated frames after it, so writing a study of any size takes one frame of memory. A port of the
 * multiframe workload of the ingest performance harness in the XNAT repository.
 */
public final class SyntheticEnhancedMr {
    private SyntheticEnhancedMr() {
    }

    /**
     * One study of uncompressed Enhanced MR objects, each its own series, which XNAT archives as an MR session with one
     * scan per object. SC Multiframe would be archived as secondary resources with no scan, so the objects have to be
     * real MR ones: minimal but valid, with shared pixel measures and plane orientation, and per-frame plane position
     * and frame content.
     */
    public static TransformFunction headers(final int objectCount, final int frameCount, final int rows, final int columns) {
        return TransformFunction.generateFromScratch(() -> {
            final String studyInstanceUid = UIDUtils.createUID();
            final List<Attributes> headers = new ArrayList<>();
            for (int object = 1; object <= objectCount; object++) {
                headers.add(header(studyInstanceUid, object, frameCount, rows, columns));
            }
            return headers;
        });
    }

    /**
     * Writes a dataset from {@link #headers} as Explicit VR Little Endian, followed by the pixel data it describes: an
     * image-like gradient, different in every frame, of 12-bit values stored in 16 bits. Frame {@code f} holds
     * {@code (f * 13 % 4096 + row + column) & 0x0FFF} at each pixel.
     */
    public static class PixelDataWriter implements DicomFileWriter {
        @Override
        public File writeDicom(final Attributes instance, final Path dataDir, final int fileIndex) {
            final int    frameCount = instance.getInt(Tag.NumberOfFrames, 1);
            final int    rows       = instance.getInt(Tag.Rows, 0);
            final int    columns    = instance.getInt(Tag.Columns, 0);
            final byte[] frame      = new byte[rows * columns * 2];
            final File   file       = dataDir.resolve(fileIndex + ".dcm").toFile();
            try (final DicomOutputStream out = new DicomOutputStream(file)) {
                out.writeDataset(instance.createFileMetaInformation(UID.ExplicitVRLittleEndian), instance);
                // Pixel data is the dataset's last element, so it can follow the rest a frame at a time.
                out.writeHeader(Tag.PixelData, VR.OW, frameCount * frame.length);
                for (int index = 0; index < frameCount; index++) {
                    fill(frame, index, rows, columns);
                    out.write(frame, 0, frame.length);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to write " + file, e);
            }
            return file;
        }

        private static void fill(final byte[] frame, final int index, final int rows, final int columns) {
            int offset = 0;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    final int value = ((index * 13) % 4096 + row + column) & 0x0FFF;
                    frame[offset++] = (byte) value;
                    frame[offset++] = (byte) (value >> 8);
                }
            }
        }
    }

    private static Attributes header(final String studyInstanceUid, final int objectNumber, final int frameCount, final int rows, final int columns) {
        final Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.EnhancedMRImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "M", "NONE");
        dataset.setString(Tag.ContentDate, VR.DA, "20260101");
        dataset.setString(Tag.ContentTime, VR.TM, "120000");
        dataset.setString(Tag.StudyTime, VR.TM, "120000");
        dataset.setString(Tag.Modality, VR.CS, "MR");
        dataset.setString(Tag.InstitutionName, VR.LO, "XNAT performance tests (synthetic)");
        dataset.setString(Tag.SeriesDescription, VR.LO, "multiframe s" + objectNumber);
        dataset.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setInt(Tag.SeriesNumber, VR.IS, objectNumber);
        dataset.setInt(Tag.InstanceNumber, VR.IS, 1);
        dataset.setInt(Tag.SamplesPerPixel, VR.US, 1);
        dataset.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        dataset.setInt(Tag.NumberOfFrames, VR.IS, frameCount);
        dataset.setInt(Tag.Rows, VR.US, rows);
        dataset.setInt(Tag.Columns, VR.US, columns);
        dataset.setInt(Tag.BitsAllocated, VR.US, 16);
        dataset.setInt(Tag.BitsStored, VR.US, 16);
        dataset.setInt(Tag.HighBit, VR.US, 15);
        dataset.setInt(Tag.PixelRepresentation, VR.US, 0);

        final Attributes organization = new Attributes();
        organization.setString(Tag.DimensionOrganizationUID, VR.UI, UIDUtils.createUID());
        dataset.newSequence(Tag.DimensionOrganizationSequence, 1).add(organization);

        final Attributes measures = new Attributes();
        measures.setDouble(Tag.PixelSpacing, VR.DS, 1.0, 1.0);
        measures.setDouble(Tag.SliceThickness, VR.DS, 1.0);
        final Attributes orientation = new Attributes();
        orientation.setDouble(Tag.ImageOrientationPatient, VR.DS, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
        final Attributes shared = new Attributes();
        shared.newSequence(Tag.PixelMeasuresSequence, 1).add(measures);
        shared.newSequence(Tag.PlaneOrientationSequence, 1).add(orientation);
        dataset.newSequence(Tag.SharedFunctionalGroupsSequence, 1).add(shared);

        final Sequence perFrame = dataset.newSequence(Tag.PerFrameFunctionalGroupsSequence, frameCount);
        for (int index = 0; index < frameCount; index++) {
            final Attributes position = new Attributes();
            position.setDouble(Tag.ImagePositionPatient, VR.DS, 0.0, 0.0, index);
            final Attributes content = new Attributes();
            content.setString(Tag.StackID, VR.SH, "1");
            content.setInt(Tag.InStackPositionNumber, VR.UL, index + 1);
            content.setInt(Tag.DimensionIndexValues, VR.UL, index + 1);
            final Attributes item = new Attributes();
            item.newSequence(Tag.PlanePositionSequence, 1).add(position);
            item.newSequence(Tag.FrameContentSequence, 1).add(content);
            perFrame.add(item);
        }
        return dataset;
    }
}
