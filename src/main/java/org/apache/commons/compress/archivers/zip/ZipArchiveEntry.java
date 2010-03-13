/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.commons.compress.archivers.zip;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.ZipException;
import org.apache.commons.compress.archivers.ArchiveEntry;

/**
 * Extension that adds better handling of extra fields and provides
 * access to the internal and external file attributes.
 *
 * <p>The extra data is expected to follow the recommendation of
 * {@link <a href="http://www.pkware.com/documents/casestudies/APPNOTE.TXT">
 * APPNOTE.txt</a>}:</p>
 * <ul>
 *   <li>the extra byte array consists of a sequence of extra fields</li>
 *   <li>each extra fields starts by a two byte header id followed by
 *   a two byte sequence holding the length of the remainder of
 *   data.</li>
 * </ul>
 *
 * <p>Any extra data that cannot be parsed by the rules above will be
 * consumed as "unparseable" extra data and treated differently by the
 * methods of this class.  Versions prior to Apache Commons Compress
 * 1.1 would have thrown an exception if any attempt was made to read
 * or write extra data not conforming to the recommendation.</p>
 *
 * @NotThreadSafe
 */
public class ZipArchiveEntry extends java.util.zip.ZipEntry
    implements ArchiveEntry, Cloneable {

    public static final int PLATFORM_UNIX = 3;
    public static final int PLATFORM_FAT  = 0;
    private static final int SHORT_MASK = 0xFFFF;
    private static final int SHORT_SHIFT = 16;

    /**
     * The {@link java.util.zip.ZipEntry} base class only supports
     * the compression methods STORED and DEFLATED. We override the
     * field so that any compression methods can be used.
     * <p>
     * The default value -1 means that the method has not been specified.
     *
     * @see <a href="https://issues.apache.org/jira/browse/COMPRESS-93"
     *        >COMPRESS-93</a>
     */
    private int method = -1;

    private int internalAttributes = 0;
    private int platform = PLATFORM_FAT;
    private long externalAttributes = 0;
    private LinkedHashMap/*<ZipShort, ZipExtraField>*/ extraFields = null;
    private UnparseableExtraFieldData unparseableExtra = null;
    private String name = null;
    private GeneralPurposeBit gpb = new GeneralPurposeBit();

    /**
     * Creates a new zip entry with the specified name.
     * @param name the name of the entry
     */
    public ZipArchiveEntry(String name) {
        super(name);
        setName(name);
    }

    /**
     * Creates a new zip entry with fields taken from the specified zip entry.
     * @param entry the entry to get fields from
     * @throws ZipException on error
     */
    public ZipArchiveEntry(java.util.zip.ZipEntry entry) throws ZipException {
        super(entry);
        setName(entry.getName());
        byte[] extra = entry.getExtra();
        if (extra != null) {
            setExtraFields(ExtraFieldUtils.parse(extra, true,
                                                 ExtraFieldUtils
                                                 .UnparseableExtraField.READ));
        } else {
            // initializes extra data to an empty byte array
            setExtra();
        }
        setMethod(entry.getMethod());
    }

    /**
     * Creates a new zip entry with fields taken from the specified zip entry.
     * @param entry the entry to get fields from
     * @throws ZipException on error
     */
    public ZipArchiveEntry(ZipArchiveEntry entry) throws ZipException {
        this((java.util.zip.ZipEntry) entry);
        setInternalAttributes(entry.getInternalAttributes());
        setExternalAttributes(entry.getExternalAttributes());
        setExtraFields(entry.getExtraFields(true));
    }

    /**
     */
    protected ZipArchiveEntry() {
        this("");
    }

    public ZipArchiveEntry(File inputFile, String entryName) {
        this(inputFile.isDirectory() && !entryName.endsWith("/") ? 
             entryName + "/" : entryName);
        if (inputFile.isFile()){
            setSize(inputFile.length());
        }
        setTime(inputFile.lastModified());
        // TODO are there any other fields we can set here?
    }

    /**
     * Overwrite clone.
     * @return a cloned copy of this ZipArchiveEntry
     */
    public Object clone() {
        ZipArchiveEntry e = (ZipArchiveEntry) super.clone();

        e.setInternalAttributes(getInternalAttributes());
        e.setExternalAttributes(getExternalAttributes());
        e.setExtraFields(getExtraFields(true));
        return e;
    }

    /**
     * Returns the compression method of this entry, or -1 if the
     * compression method has not been specified.
     *
     * @return compression method
     */
    public int getMethod() {
        return method;
    }

    /**
     * Sets the compression method of this entry.
     *
     * @param method compression method
     */
    public void setMethod(int method) {
        if (method < 0) {
            throw new IllegalArgumentException(
                    "ZIP compression method can not be negative: " + method);
        }
        this.method = method;
    }

    /**
     * Retrieves the internal file attributes.
     *
     * @return the internal file attributes
     */
    public int getInternalAttributes() {
        return internalAttributes;
    }

    /**
     * Sets the internal file attributes.
     * @param value an <code>int</code> value
     */
    public void setInternalAttributes(int value) {
        internalAttributes = value;
    }

    /**
     * Retrieves the external file attributes.
     * @return the external file attributes
     */
    public long getExternalAttributes() {
        return externalAttributes;
    }

    /**
     * Sets the external file attributes.
     * @param value an <code>long</code> value
     */
    public void setExternalAttributes(long value) {
        externalAttributes = value;
    }

    /**
     * Sets Unix permissions in a way that is understood by Info-Zip's
     * unzip command.
     * @param mode an <code>int</code> value
     */
    public void setUnixMode(int mode) {
        // CheckStyle:MagicNumberCheck OFF - no point
        setExternalAttributes((mode << SHORT_SHIFT)
                              // MS-DOS read-only attribute
                              | ((mode & 0200) == 0 ? 1 : 0)
                              // MS-DOS directory flag
                              | (isDirectory() ? 0x10 : 0));
        // CheckStyle:MagicNumberCheck ON
        platform = PLATFORM_UNIX;
    }

    /**
     * Unix permission.
     * @return the unix permissions
     */
    public int getUnixMode() {
        return platform != PLATFORM_UNIX ? 0 :
            (int) ((getExternalAttributes() >> SHORT_SHIFT) & SHORT_MASK);
    }

    /**
     * Platform specification to put into the &quot;version made
     * by&quot; part of the central file header.
     *
     * @return PLATFORM_FAT unless {@link #setUnixMode setUnixMode}
     * has been called, in which case PLATORM_UNIX will be returned.
     */
    public int getPlatform() {
        return platform;
    }

    /**
     * Set the platform (UNIX or FAT).
     * @param platform an <code>int</code> value - 0 is FAT, 3 is UNIX
     */
    protected void setPlatform(int platform) {
        this.platform = platform;
    }

    /**
     * Replaces all currently attached extra fields with the new array.
     * @param fields an array of extra fields
     */
    public void setExtraFields(ZipExtraField[] fields) {
        extraFields = new LinkedHashMap();
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] instanceof UnparseableExtraFieldData) {
                unparseableExtra = (UnparseableExtraFieldData) fields[i];
            } else {
                extraFields.put(fields[i].getHeaderId(), fields[i]);
            }
        }
        setExtra();
    }

    /**
     * Retrieves all extra fields that have been parsed successfully.
     * @return an array of the extra fields
     */
    public ZipExtraField[] getExtraFields() {
        return getExtraFields(false);
    }

    /**
     * Retrieves extra fields.
     * @param includeUnparseable whether to also return unparseable
     * extra fields as {@link UnparseableExtraFieldData} if such data
     * exists.
     * @return an array of the extra fields
     */
    public ZipExtraField[] getExtraFields(boolean includeUnparseable) {
        if (extraFields == null) {
            return !includeUnparseable || unparseableExtra == null
                ? new ZipExtraField[0]
                : new ZipExtraField[] { unparseableExtra };
        }
        List result = new ArrayList(extraFields.values());
        if (includeUnparseable && unparseableExtra != null) {
            result.add(unparseableExtra);
        }
        return (ZipExtraField[]) result.toArray(new ZipExtraField[0]);
    }

    /**
     * Adds an extra field - replacing an already present extra field
     * of the same type.
     *
     * <p>If no extra field of the same type exists, the field will be
     * added as last field.</p>
     * @param ze an extra field
     */
    public void addExtraField(ZipExtraField ze) {
        if (ze instanceof UnparseableExtraFieldData) {
            unparseableExtra = (UnparseableExtraFieldData) ze;
        } else {
            if (extraFields == null) {
                extraFields = new LinkedHashMap();
            }
            extraFields.put(ze.getHeaderId(), ze);
        }
        setExtra();
    }

    /**
     * Adds an extra field - replacing an already present extra field
     * of the same type.
     *
     * <p>The new extra field will be the first one.</p>
     * @param ze an extra field
     */
    public void addAsFirstExtraField(ZipExtraField ze) {
        if (ze instanceof UnparseableExtraFieldData) {
            unparseableExtra = (UnparseableExtraFieldData) ze;
        } else {
            LinkedHashMap copy = extraFields;
            extraFields = new LinkedHashMap();
            extraFields.put(ze.getHeaderId(), ze);
            if (copy != null) {
                copy.remove(ze.getHeaderId());
                extraFields.putAll(copy);
            }
        }
        setExtra();
    }

    /**
     * Remove an extra field.
     * @param type the type of extra field to remove
     */
    public void removeExtraField(ZipShort type) {
        if (extraFields == null) {
            throw new java.util.NoSuchElementException();
        }
        if (extraFields.remove(type) == null) {
            throw new java.util.NoSuchElementException();
        }
        setExtra();
    }

    /**
     * Removes unparseable extra field data.
     */
    public void removeUnparseableExtraFieldData() {
        if (unparseableExtra == null) {
            throw new java.util.NoSuchElementException();
        }
        unparseableExtra = null;
        setExtra();
    }

    /**
     * Looks up an extra field by its header id.
     *
     * @return null if no such field exists.
     */
    public ZipExtraField getExtraField(ZipShort type) {
        if (extraFields != null) {
            return (ZipExtraField) extraFields.get(type);
        }
        return null;
    }

    /**
     * Looks up extra field data that couldn't be parsed correctly.
     *
     * @return null if no such field exists.
     */
    public UnparseableExtraFieldData getUnparseableExtraFieldData() {
        return unparseableExtra;
    }

    /**
     * Parses the given bytes as extra field data and consumes any
     * unparseable data as an {@link UnparseableExtraFieldData}
     * instance.
     * @param extra an array of bytes to be parsed into extra fields
     * @throws RuntimeException if the bytes cannot be parsed
     * @throws RuntimeException on error
     */
    public void setExtra(byte[] extra) throws RuntimeException {
        try {
            ZipExtraField[] local =
                ExtraFieldUtils.parse(extra, true,
                                      ExtraFieldUtils.UnparseableExtraField.READ);
            mergeExtraFields(local, true);
        } catch (ZipException e) {
            // actually this is not be possible as of Commons Compress 1.1
            throw new RuntimeException("Error parsing extra fields for entry: "
                                       + getName() + " - " + e.getMessage(), e);
        }
    }

    /**
     * Unfortunately {@link java.util.zip.ZipOutputStream
     * java.util.zip.ZipOutputStream} seems to access the extra data
     * directly, so overriding getExtra doesn't help - we need to
     * modify super's data directly.
     */
    protected void setExtra() {
        super.setExtra(ExtraFieldUtils.mergeLocalFileDataData(getExtraFields(true)));
    }

    /**
     * Sets the central directory part of extra fields.
     */
    public void setCentralDirectoryExtra(byte[] b) {
        try {
            ZipExtraField[] central =
                ExtraFieldUtils.parse(b, false,
                                      ExtraFieldUtils.UnparseableExtraField.READ);
            mergeExtraFields(central, false);
        } catch (ZipException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Retrieves the extra data for the local file data.
     * @return the extra data for local file
     */
    public byte[] getLocalFileDataExtra() {
        byte[] extra = getExtra();
        return extra != null ? extra : new byte[0];
    }

    /**
     * Retrieves the extra data for the central directory.
     * @return the central directory extra data
     */
    public byte[] getCentralDirectoryExtra() {
        return ExtraFieldUtils.mergeCentralDirectoryData(getExtraFields(true));
    }

    /**
     * Get the name of the entry.
     * @return the entry name
     */
    public String getName() {
        return name == null ? super.getName() : name;
    }

    /**
     * Is this entry a directory?
     * @return true if the entry is a directory
     */
    public boolean isDirectory() {
        return getName().endsWith("/");
    }

    /**
     * Set the name of the entry.
     * @param name the name to use
     */
    protected void setName(String name) {
        this.name = name;
    }

    /**
     * Get the hashCode of the entry.
     * This uses the name as the hashcode.
     * @return a hashcode.
     */
    public int hashCode() {
        // this method has severe consequences on performance. We cannot rely
        // on the super.hashCode() method since super.getName() always return
        // the empty string in the current implemention (there's no setter)
        // so it is basically draining the performance of a hashmap lookup
        return getName().hashCode();
    }

    /**
     * The "general purpose bit" field.
     * @since Apache Commons Compress 1.1
     */
    public GeneralPurposeBit getGeneralPurposeBit() {
        return gpb;
    }

    /**
     * The "general purpose bit" field.
     * @since Apache Commons Compress 1.1
     */
    public void setGeneralPurposeBit(GeneralPurposeBit b) {
        gpb = b;
    }

    /**
     * If there are no extra fields, use the given fields as new extra
     * data - otherwise merge the fields assuming the existing fields
     * and the new fields stem from different locations inside the
     * archive.
     * @param f the extra fields to merge
     * @param local whether the new fields originate from local data
     */
    private void mergeExtraFields(ZipExtraField[] f, boolean local)
        throws ZipException {
        if (extraFields == null) {
            setExtraFields(f);
        } else {
            for (int i = 0; i < f.length; i++) {
                ZipExtraField existing;
                if (f[i] instanceof UnparseableExtraFieldData) {
                    existing = unparseableExtra;
                } else {
                    existing = getExtraField(f[i].getHeaderId());
                }
                if (existing == null) {
                    addExtraField(f[i]);
                } else {
                    if (local) {
                        byte[] b = f[i].getLocalFileDataData();
                        existing.parseFromLocalFileData(b, 0, b.length);
                    } else {
                        byte[] b = f[i].getCentralDirectoryData();
                        existing.parseFromCentralDirectoryData(b, 0, b.length);
                    }
                }
            }
            setExtra();
        }
    }

    /** {@inheritDocs} */
    public Date getLastModifiedDate() {
        return new Date(getTime());
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ZipArchiveEntry other = (ZipArchiveEntry) obj;
        String myName = getName();
        String otherName = other.getName();
        if (myName == null) {
            if (otherName != null) {
                return false;
            }
        } else if (!myName.equals(otherName)) {
            return false;
        }
        String myComment = getComment();
        String otherComment = other.getComment();
        if (myComment == null) {
            if (otherComment != null) {
                return false;
            }
        } else if (!myComment.equals(otherComment)) {
            return false;
        }
        return getTime() == other.getTime()
            && getInternalAttributes() == other.getInternalAttributes()
            && getPlatform() == other.getPlatform()
            && getExternalAttributes() == other.getExternalAttributes()
            && getMethod() == other.getMethod()
            && getSize() == other.getSize()
            && getCrc() == other.getCrc()
            && getCompressedSize() == other.getCompressedSize()
            && Arrays.equals(getCentralDirectoryExtra(),
                             other.getCentralDirectoryExtra())
            && Arrays.equals(getLocalFileDataExtra(),
                             other.getLocalFileDataExtra())
            && gpb.equals(other.gpb);
    }
}
