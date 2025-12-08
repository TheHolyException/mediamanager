package de.theholyexception.mediamanager.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class MP4Utils {

	private MP4Utils() {}

	public static long getVideoDurationSeconds(File mp4File) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(mp4File, "r")) {
			long fileSize = raf.length();
			long position = 0;

			while (position < fileSize - 8) {
				raf.seek(position);

				// Read box size (4 bytes) and type (4 bytes)
				long boxSize = readUInt32(raf);
				String boxType = readString(raf, 4);

				if (boxSize == 1) {
					// Extended size (64-bit)
					boxSize = readUInt64(raf);
					position += 16;
				} else if (boxSize == 0) {
					// Box extends to end of file
					boxSize = fileSize - position;
				} else {
					position += 8;
				}

				if ("moov".equals(boxType)) {
					return parseMoovBox(raf, position, boxSize - 8);
				}

				position += boxSize - 8;
			}
		}
		return -1; // Duration not found
	}

	private static long parseMoovBox(RandomAccessFile raf, long startPos, long boxSize) throws IOException {
		long position = startPos;
		long endPos = startPos + boxSize;

		while (position < endPos - 8) {
			raf.seek(position);
			long subBoxSize = readUInt32(raf);
			String subBoxType = readString(raf, 4);

			if ("mvhd".equals(subBoxType)) {
				return parseMvhdBox(raf);
			}

			position += subBoxSize;
		}
		return -1;
	}

	private static long parseMvhdBox(RandomAccessFile raf) throws IOException {
		int version = raf.readByte();
		raf.seek(raf.getFilePointer() + 3); // flags

		if (version == 1) {
			raf.seek(raf.getFilePointer() + 16); // creation + modification time (64-bit)
			long timescale = readUInt32(raf);
			long duration = readUInt64(raf);
			return duration / timescale;
		} else {
			raf.seek(raf.getFilePointer() + 8); // creation + modification time (32-bit)
			long timescale = readUInt32(raf);
			long duration = readUInt32(raf);
			return duration / timescale;
		}
	}

	private static long readUInt32(RandomAccessFile raf) throws IOException {
		return ((long) raf.readByte() & 0xFF) << 24 |
			((long) raf.readByte() & 0xFF) << 16 |
			((long) raf.readByte() & 0xFF) << 8 |
			((long) raf.readByte() & 0xFF);
	}

	private static long readUInt64(RandomAccessFile raf) throws IOException {
		return ((long) raf.readByte() & 0xFF) << 56 |
			((long) raf.readByte() & 0xFF) << 48 |
			((long) raf.readByte() & 0xFF) << 40 |
			((long) raf.readByte() & 0xFF) << 32 |
			((long) raf.readByte() & 0xFF) << 24 |
			((long) raf.readByte() & 0xFF) << 16 |
			((long) raf.readByte() & 0xFF) << 8 |
			((long) raf.readByte() & 0xFF);
	}

	private static String readString(RandomAccessFile raf, int length) throws IOException {
		byte[] bytes = new byte[length];
		raf.readFully(bytes);
		return new String(bytes, StandardCharsets.US_ASCII);
	}


}
