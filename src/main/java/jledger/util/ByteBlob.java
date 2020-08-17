// Copyright 2020 David J. Pearce
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package jledger.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jledger.core.Content;
import jledger.core.Content.Blob;

/**
 *
 * @author David J. Pearce
 *
 */
public class ByteBlob implements Content.Blob {
	protected byte[] bytes;

	public ByteBlob(byte[] data) {
		this.bytes = data;
	}

	@Override
	public int size() {
		return bytes.length;
	}

	@Override
	public byte[] get() {
		return bytes;
	}

	@Override
	public byte read(int index) {
		return bytes[index];
	}

	@Override
	public Diff write(int index, byte b) {
		return new Diff(this, new Replacement(index, 1, b));
	}

	@Override
	public Diff replace(int index, int length, byte... bytes) {
		return new Diff(this, new Replacement(index, length, bytes));
	}

	/**
	 * Construct a diff between two arrays of bytes using the <i>longest common
	 * subsequence</i> algorithm.
	 *
	 * @param before
	 * @param after
	 * @return
	 */
	public static Diff diff(byte[] before, byte[] after) {
		// Apply the LCS algorithm
		int[] mapping = longestCommonSubsequence(before, after);
		// Convert mapping to deltas
		List<Replacement> deltas = extractDeltas(mapping, after);
		// Contruct final diff.
		return new Diff(new ByteBlob(before), deltas.toArray(new Replacement[deltas.size()]));
	}

	@Override
	public String toString() {
		return Arrays.toString(bytes);
	}

	/**
	 * <p>
	 * Represents a sequence of one or more updates to a given byte array. For
	 * example, consider the following update to a given array:
	 * </p>
	 *
	 * <pre>
	 *  0 1 2 3 4 5 6 7 8 9 A
	 * +-+-+-+-+-+-+-+-+-+-+-+
	 * |H|e|l|l|o| |W|o|r|l|d|
	 * +-+-+-+-+-+-+-+-+-+-+-+
	 *
	 *           \/
	 *           \/
	 *
	 * +-+-+-+-+-+-+-+-+-+-+
	 * |h|e|l|l|o|w|o|r|l|d|
	 * +-+-+-+-+-+-+-+-+-+-+
	 * </pre>
	 *
	 * <p>
	 * This change can be encoded using a <code>Diff</code> containing three deltas
	 * as follows: <code>(0,1,"h");(5,1,"");(6,1,"w")</code>. For example,
	 * <code>(0,1,"h")</code> means replace the region from <code>0</code> to
	 * <code>1</code> (exclusive)
	 *
	 * </p>
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Diff implements Content.Diff {
		private final Content.Blob parent;
		private final Replacement[] replacements;

		/**
		 * Construct a diff from a given sequence of one or more non-overlapping deltas
		 * in sorted order.
		 *
		 * @param deltas
		 */
		private Diff(Content.Blob parent, Replacement... deltas) {
			if (deltas == null) {
				throw new IllegalArgumentException("null deltas");
			} else if (!ArrayUtils.isSorted(deltas)) {
				throw new IllegalArgumentException("unsorted deltas");
			} else if (!areDisjoint(deltas)) {
				throw new IllegalArgumentException("overlapping deltas");
			}
			this.parent = parent;
			this.replacements = deltas;
		}


		@Override
		public int size() {
			int diff = 0;
			for (int i = 0; i != replacements.length; ++i) {
				diff += replacements[i].diff();
			}
			return parent.size() + diff;
		}

		@Override
		public Blob parent() {
			return parent;
		}

		@Override
		public byte read(int index) {
			for(int i=0;i!=replacements.length;++i) {
				Replacement ith = replacements[i];
				if(ith.offset > index) {
					return parent.read(index);
				} else if(index < (ith.offset + ith.bytes.length)) {
					return ith.bytes[index - ith.offset];
				} else {
					index = index - ith.diff();
				}
			}
			return parent.read(index);
		}

		@Override
		public Diff write(int index, byte b) {
			// NOTE: compress diffs?
			return new Diff(this, new Replacement(index, 1, b));
		}

		@Override
		public Diff replace(int index, int length, byte... bytes) {
			// NOTE: compress diffs?
			return new Diff(this, new Replacement(index, length, bytes));
		}

		@Override
		public int count() {
			return replacements.length;
		}

		@Override
		public Replacement getReplacement(int i) {
			return replacements[i];
		}

		@Override
		public byte[] get() {
			// NOTE: a more efficient way of doing this might be to create an array of the
			// right size and then write parent data into it?
			//
			// Extract the parent bytes
			final byte[] bytes = parent.get();
			// Determine size of final array
			final int size = size();
			// Construct final array
			byte[] result = new byte[size];
			int opos = 0;
			int rpos = 0;
			// Apply delta's one-by-one
			for (int i = 0; i < replacements.length; ++i) {
				final Replacement ith = replacements[i];
				final byte[] ithbytes = ith.bytes;
				// Calculate gap
				int gap = ith.offset - opos;
				// Copy section up to delta start
				System.arraycopy(bytes, opos, result, rpos, gap);
				// move pointer along
				rpos = rpos + gap;
				// Copy delta replacement itself
				System.arraycopy(ithbytes, 0, result, rpos, ithbytes.length);
				// move pointers along
				opos = opos + gap + ith.length;
				rpos = rpos + ithbytes.length;
			}
			// Finally, copy over any remaining bytes from original sequence.
			System.arraycopy(bytes, opos, result, rpos, bytes.length - opos);
			//
			return result;
		}

		@Override
		public String toString() {
			String r = "";
			if(replacements.length > 0) {
				r += replacements[0].toString();
				for(int i=1;i!=replacements.length;++i) {
					r += "," + replacements[i].toString();
				}
			}
			return "{" + r + "}";
		}
	}


	/**
	 * <p>
	 * Represents an atomic replacement in the original array. Specifically, a
	 * region in the original array is replaced by a new sequence of bytes. Observe
	 * that this region may be larger or smaller than the original region. The
	 * following illustrates:
	 * </p>
	 *
	 * <pre>
	 *  0 1 2 3 4 5 6 7 8 9 A B
	 * +-+-+-+-+-+-+-+-+-+-+-+-+
	 * |H|e|L|L|L|O| |W|o|r|l|d|
	 * +-+-+-+-+-+-+-+-+-+-+-+-+
	 *     | : : : |
	 *     +-------+
	 *         |
	 *        \|/
	 *      +-+-+-+
	 *      |l|l|o|
	 *      +-+-+-+
	 * </pre>
	 *
	 * <p>
	 * The above illustrates the region "LLLO" from the original sequence being
	 * replaced by "llo". Thus, after applying the delta, we have the string "Hello
	 * World".
	 * </p>
	 *
	 * @author David J. Pearce
	 *
	 */
	private static class Replacement implements Content.Replacement, Comparable<Replacement> {
		/**
		 * Offset in the original sequence where region being replaced begins.
		 */
		protected final int offset;
		/**
		 * Length of region in the original sequence being replaced.
		 */
		protected final int length;

		/**
		 * Sequence which replaced the region from the original sequence.
		 */
		private final byte[] bytes;

		public Replacement(int offset, int length, byte... bytes) {
			if(offset < 0) {
				throw new IllegalArgumentException("negative offset");
			} else if(length < 0) {
				throw new IllegalArgumentException("negative length");
			} else if (bytes == null) {
				throw new IllegalArgumentException("null bytes");
			}
			this.offset = offset;
			this.length = length;
			this.bytes = bytes;
		}

		@Override
		public int offset() {
			return offset;
		}

		@Override
		public int size() {
			return length;
		}

		/**
		 * Calculate the overall change in length of the original sequence resulting
		 * from this delta.  Thus,
		 *
		 * @return
		 */
		public int diff() {
			return bytes.length - length;
		}

		/**
		 * Get the bytes which replace the region in the original array.
		 *
		 * @return
		 */
		@Override
		public byte[] bytes() {
			return bytes;
		}

		/**
		 * Check this delta is disjoint with another (i.e. they don't overlap).
		 *
		 * @param other
		 * @return
		 */
		public boolean disjoint(Replacement other) {
			int end = (offset + length) - 1;
			int oend = (other.offset + other.length) - 1;
			return (end < other.offset) || (oend < offset);
		}

		@Override
		public int compareTo(Replacement o) {
			if (offset < o.offset) {
				return -1;
			} else if (offset > o.offset) {
				return 1;
			}
			int end = (offset + length) - 1;
			int oend = (o.offset + o.length) - 1;
			if (end < oend) {
				return -1;
			} else if (end > oend) {
				return 1;
			} else {
				return 0;
			}
		}

		@Override
		public String toString() {
			return "(" + offset + ";" + length + ";" + Arrays.toString(bytes) + ")";
		}
	}

	/**
	 * Check whether a given array of delta's are overlapping or not.
	 *
	 * @param deltas
	 * @return
	 */
	protected static boolean areDisjoint(Replacement[] deltas) {
		for (int i = 1; i < deltas.length; ++i) {
			Replacement ithm1 = deltas[i - 1];
			Replacement ith = deltas[i];
			if (!ithm1.disjoint(ith)) {
				return false;
			}
			ithm1 = ith;
		}
		return true;
	}

	/**
	 * <p>
	 * Extract delta's using a given mapping from the before sequence to the after
	 * sequence, as generated from the <i>least common subsequence</i> algorithm.
	 * For example:
	 * </p>
	 *
	 * <pre>
	 *  0 1 2
	 * +-+-+-+-+-+
	 * |a|b|c|d|e| (before)
	 * +-+-+-+-+-+
	 *    | |
	 *   / /
	 *  | |
	 * +-+-+-+-+
	 * |b|c|f|g| (after)
	 * +-+-+-+-+
	 * </pre>
	 *
	 * <p>
	 * In this case, the mapping would be <code>[-1,0,1,-1,-1]</code> which
	 * indicates positions <code>0</code>, <code>3</code> and <code>4</code> are
	 * removed whilst positions <code>1</code> and <code>2</code> correspond to
	 * positions <code>0</code> and <code>1</code> in the final sequence.
	 * </p>
	 *
	 * <p>
	 * The current extraction mechanism could still be improved in that it can
	 * generate lots of small delta's when a single large one would be more
	 * sensible. Potentially, some form of post processing could coalesce delta's
	 * as necessary.
	 * </p>
	 *
	 * @param mapping
	 * @param after
	 * @return
	 */
	private static List<Replacement> extractDeltas(int[] mapping, byte[] after) {
		ArrayList<Replacement> deltas = new ArrayList<>();
		// Initialise after markers
		int aStart = 0, aPos = 0;
		// Initialise before markers
		int bStart = 0, bPos = 0;
		// Proceed extracting delta's
		while (bPos < mapping.length && aPos < after.length) {
			if (mapping[bPos] > aPos) {
				// Uneven case. Increase after buffer
				aPos = mapping[bPos];
			} else if (mapping[bPos] < aPos) {
				// Uneven case. Increase before buffer
				bPos = bPos + 1;
			} else {
				// Matching case. Flush buffers and advance
				if (bStart < bPos || aStart < aPos) {
					byte[] additions = Arrays.copyOfRange(after, aStart, aPos);
					deltas.add(new Replacement(bStart, bPos - bStart, additions));
				}
				aStart = aPos = aPos + 1;
				bStart = bPos = bPos + 1;
			}
		}
		// Flush remaining buffers
		if (bStart < mapping.length || aStart < after.length) {
			// Terminating case. Flush buffers and end.
			byte[] additions = Arrays.copyOfRange(after, aStart, after.length);
			deltas.add(new Replacement(bStart, mapping.length - bStart, additions));
		}
		//
		return deltas;
	}

	/**
	 * <p>
	 * Determine the longest common subsequence of two sequences. For example,
	 * suppose <code>X=[1,2,2,3,2,3,4]</code> and <code>Y=[2,2,5,3,4,5]</code> then
	 * a <i>common subsequence</code> is <code>[2,2]</code> and another is
	 * <code>[2,3,4]</code>. However, the <i>longest common subsequence</code> is
	 * <code>[2,2,3,4]</code>.
	 * </p>
	 *
	 * <p>
	 * This algorithm produces a mapping from elements in X to elements in Y. For
	 * the above example, it might produce <code>[-1,0,1,3,-1,-1]</code>. Here,
	 * <code>-1</code> indicates the element in <code>X</code> does not match an
	 * element in <code>Y</code>. In contrast, non-negative values are the matching
	 * indices in <code>Y</code>.
	 * </p>
	 *
	 * <p>
	 * <b>NOTE:</b> Whilst the above example had only one longest common
	 * subsequence, it's not always the case there is only one. This algorithm
	 * simply returns <i>a</i> longest common subsequence.
	 * </p>
	 *
	 *
	 * @param X The left sequence
	 * @param Y The right sequence
	 * @return The resulting mapping
	 */
	private static int[] longestCommonSubsequence(byte[] X, byte[] Y) {
		final int m = X.length + 1;
		final int n = Y.length + 1;
		final int[] C = new int[m * n];
		// Calculate the lengths
		for (int i = 1; i < m; ++i) {
			for (int j = 1; j < n; ++j) {
				int ij = i + (j * m);
				if (X[i - 1] == Y[j - 1]) {
					C[ij] = C[(i - 1) + ((j - 1) * m)] + 1;
				} else {
					final int Cim1j = C[(i - 1) + (j * m)];
					final int Cijm1 = C[i + ((j - 1) * m)];
					C[ij] = (Cim1j >= Cijm1) ? Cim1j : Cijm1;
				}
			}
		}
		// Finally, extract the LCS
		int[] Z = new int[X.length];
		Arrays.fill(Z, -1);
		extractSubsequence(C, Z, m - 1, n - 1);
		return Z;
	}


	protected static void extractSubsequence(int[] C, int[] Z, int i, int j) {
		final int m = Z.length + 1;
		if (i > 0 && j > 0) {
			int Cij = C[i + (j * m)];
			final int Cim1j = C[(i - 1) + (j * m)];
			final int Cijm1 = C[i + ((j - 1) * m)];
			if (Cij == Cim1j) {
				Z[i - 1] = -1;
				extractSubsequence(C, Z, i - 1, j);
			} else if (Cij == Cijm1) {
				Z[i - 1] = -1;
				extractSubsequence(C, Z, i, j - 1);
			} else {
				extractSubsequence(C, Z, i - 1, j - 1);
				Z[i - 1] = j - 1;
			}
		}
	}

	/**
	 * Useful for debugging.
	 *
	 * @param matrix
	 * @param m
	 * @param n
	 */
	protected static void printMatrix(int[] matrix, int m, int n) {
		System.out.print("   ");
		for (int i = 0; i < m; ++i) {
			System.out.print(String.format("%02d", i) + " ");
		}
		System.out.println();
		System.out.print("  +");
		for (int k = 0; k < m; ++k) {
			System.out.print("--+");
		}
		System.out.println();
		for (int j = 0; j < n; ++j) {
			System.out.print(String.format("%02d|", j));
			for (int i = 0; i < m; ++i) {
				int ij = i + (j * m);
				System.out.print(String.format("%02d|", matrix[ij]));
			}
			System.out.println();
			System.out.print("  +");
			for (int k = 0; k < m; ++k) {
				System.out.print("--+");
			}
			System.out.println();
		}
	}

	public static void main(String[] args) {
		byte[] bs = "Hello World".getBytes();
		Content.Blob blob = new ByteBlob(bs);
		blob = blob.replace(0, 0, "______".getBytes());
		//
		for(int i=0;i!=blob.size();++i) {
			char c = (char) blob.read(i);
			System.out.print(c);
		}
		System.out.println();
	}
}