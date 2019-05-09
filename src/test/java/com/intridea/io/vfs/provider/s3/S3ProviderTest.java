package com.intridea.io.vfs.provider.s3;

import com.amazonaws.services.s3.transfer.TransferManagerConfiguration;
import com.github.vfss3.S3FileObject;
import com.github.vfss3.S3FileProvider;
import com.github.vfss3.S3FileSystemOptions;
import com.intridea.io.vfs.operations.IMD5HashGetter;
import com.intridea.io.vfs.operations.IPublicUrlsGetter;
import com.intridea.io.vfs.support.AbstractS3FileSystemTest;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.provider.AbstractFileSystem;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;
import static com.intridea.io.vfs.FileAssert.assertHasChildren;
import static org.apache.commons.vfs2.Selectors.SELECT_ALL;
import static org.testng.Assert.*;

public class S3ProviderTest extends AbstractS3FileSystemTest {
    private static final String BIG_FILE = "big_file.iso";

    private String fileName, encryptedFileName, dirName;
    private FileObject file, dir;

    @BeforeClass
    public void setUp() throws IOException {
        Random r = new Random();
        encryptedFileName = "vfs-encrypted-file" + r.nextInt(1000);
        fileName = "vfs-file" + r.nextInt(1000);
        dirName = "vfs-dir" + r.nextInt(1000);
    }

    @Test
    public void createFileOk() throws FileSystemException {
        file = env.resolveFile("/test-place/%s", fileName);
        file.createFile();
        assertTrue(file.exists());
    }

    @Test(dependsOnMethods = {"createFileOk"})
    public void createEncryptedFileOk() throws FileSystemException {
        final S3FileSystemOptions options = new S3FileSystemOptions();

        options.setServerSideEncryption(true);

        file = env.resolveFile(options, "/test-place/%s", encryptedFileName);

        file.createFile();

        assertTrue(file.exists());
        assertEquals(((S3FileObject) file).getObjectMetadata().getSSEAlgorithm(), AES_256_SERVER_SIDE_ENCRYPTION);
    }

    @Test(expectedExceptions={FileSystemException.class})
    public void createFileFailed() throws FileSystemException {
        FileObject tmpFile = vfs.resolveFile("s3://../new-mpoint/vfs-bad-file");
        tmpFile.createFile();
    }

    /**
     * Create folder on already existed file
     * @throws FileSystemException
     */
    @Test(expectedExceptions={FileSystemException.class}, dependsOnMethods={"createFileOk"})
    public void createFileFailed2() throws FileSystemException {
        FileObject tmpFile = env.resolveFile("/test-place/%s", fileName);
        tmpFile.createFolder();
    }

    @Test
    public void createDirOk() throws FileSystemException {
        dir = env.resolveFile("/test-place/%s", dirName);
        dir.createFolder();
        assertTrue(dir.exists());
    }

    @Test(expectedExceptions={FileSystemException.class})
    public void createDirFailed() throws FileSystemException {
        FileObject tmpFile = vfs.resolveFile("s3://../new-mpoint/vfs-bad-dir");
        tmpFile.createFolder();
    }

    /**
     * Create file on already existed folder
     * @throws FileSystemException
     */
    @Test(expectedExceptions = FileSystemException.class, dependsOnMethods = "createDirOk")
    public void createDirFailed2() throws FileSystemException {
        FileObject tmpFile = env.resolveFile("/test-place/%s", dirName);
        tmpFile.createFile();
    }

    @Test(dependsOnMethods={"upload"})
    public void exists() throws IOException {
        // Existed dir
        FileObject existedDir = env.resolveFile("/test-place");
        assertTrue(existedDir.exists());

        // Non-existed dir
        FileObject nonExistedDir = vfs.resolveFile(existedDir, "path/to/non/existed/dir");
        Assert.assertFalse(nonExistedDir.exists());

        // Existed file
        FileObject existedFile = env.resolveFile("/test-place/backup.zip");
        assertTrue(existedFile.exists());

        // Non-existed file
        FileObject nonExistedFile = env.resolveFile("/ne/bыlo/i/net");
        Assert.assertFalse(nonExistedFile.exists());
    }

    @Test(dependsOnMethods={"createFileOk"})
    public void upload() throws IOException {
        FileObject dest = env.resolveFile("/test-place/backup.zip");

        // Delete file if exists
        if (dest.exists()) {
            dest.delete();
        }

        // Copy data
        final File backupFile = new File(env.binaryFile());

        assertTrue(backupFile.exists(), "Backup file should exists");

        FileObject src = vfs.resolveFile(backupFile.getAbsolutePath());
        dest.copyFrom(src, Selectors.SELECT_SELF);

        assertTrue(dest.exists() && dest.getType().equals(FileType.FILE));
        assertEquals(((S3FileObject)dest).getObjectMetadata().getSSEAlgorithm(),
            null);
    }

    @Test(dependsOnMethods = {"createEncryptedFileOk"})
    public void uploadEncrypted() throws IOException {
        final S3FileSystemOptions options = new S3FileSystemOptions();

        options.setServerSideEncryption(true);

        FileObject dest = env.resolveFile(options, "/test-place/backup.zip");

        // Delete file if exists
        if (dest.exists()) {
            dest.delete();
        }

        // Copy data
        final File backupFile = new File(env.binaryFile());

        assertTrue(backupFile.exists(), "Backup file should exists");

        FileObject src = vfs.resolveFile(backupFile.getAbsolutePath());
        dest.copyFrom(src, Selectors.SELECT_SELF);

        assertTrue(dest.exists() && dest.getType().equals(FileType.FILE));
        assertEquals(((S3FileObject)dest).getObjectMetadata().getSSEAlgorithm(), AES_256_SERVER_SIDE_ENCRYPTION);
    }

    @Test(dependsOnMethods={"createFileOk"})
    public void uploadMultiple() throws Exception {
        FileObject dest = env.resolveFile("/test-place/backup.zip");

        // Delete file if exists
        if (dest.exists()) {
            dest.delete();
        }

        // Copy data
        final File backupFile = new File(env.binaryFile());

        assertTrue(backupFile.exists(), "Backup file should exists");

        FileObject src = vfs.resolveFile(backupFile.getAbsolutePath());

        // copy twice
        dest.copyFrom(src, Selectors.SELECT_SELF);
        Thread.sleep(2000L);
        dest.copyFrom(src, Selectors.SELECT_SELF);

        assertTrue(dest.exists() && dest.getType().equals(FileType.FILE));
    }

    @Test(dependsOnMethods={"createFileOk"})
    public void uploadBigFile() throws IOException {
        S3FileSystemOptions fso = new S3FileSystemOptions();

        fso.setMaxUploadThreads(10);

        FileObject dest = env.resolveFile(fso, "/%s", BIG_FILE);

        // Delete file if exists
        if (dest.exists()) {
            dest.delete();
        }

        // Copy data
        FileObject src = vfs.resolveFile(env.bigFile());

        assertTrue(src.exists(), "Big file should exists");

        assertTrue(src.getContent().getSize() > (new TransferManagerConfiguration()).getMultipartUploadThreshold());

        dest.copyFrom(src, Selectors.SELECT_SELF);

        assertTrue(dest.exists(), "Destination file should be on place");
        assertEquals(dest.getType(), FileType.FILE);
        assertEquals(src.getContent().getSize(), dest.getContent().getSize());
    }

    @Test(dependsOnMethods={"createFileOk"})
    public void outputStream() throws IOException {
        FileObject dest = env.resolveFile("/test-place/output.txt");

        // Delete file if exists
        if (dest.exists()) {
            dest.delete();
        }

        // Copy data
        OutputStream os = dest.getContent().getOutputStream();
        try {
            os.write(env.binaryFile().getBytes("US-ASCII"));
        } finally {
            os.close();
        }
        assertTrue(dest.exists() && dest.getType().equals(FileType.FILE));
        assertEquals(dest.getContent().getSize(), env.binaryFile().length());
        assertEquals(((S3FileObject)dest).getObjectMetadata().getSSEAlgorithm(),
                null);
        BufferedReader reader = new BufferedReader(new InputStreamReader(dest.getContent().getInputStream(), "US-ASCII"));
        try {
            assertEquals(reader.readLine(), env.binaryFile());
        } finally {
            reader.close();
        }
        dest.delete();
    }

    @Test(dependsOnMethods = {"createEncryptedFileOk"})
    public void outputStreamEncrypted() throws IOException {
        final S3FileSystemOptions options = new S3FileSystemOptions();

        options.setServerSideEncryption(true);

        FileObject dest = env.resolveFile(options, "/test-place/output.txt");

        // Delete file if exists
        if (dest.exists()) {
            dest.delete();
        }

        // Copy data
        OutputStream os = dest.getContent().getOutputStream();
        try {
            os.write(env.binaryFile().getBytes("US-ASCII"));
        } finally {
            os.close();
        }
        assertTrue(dest.exists() && dest.getType().equals(FileType.FILE));
        assertEquals(dest.getContent().getSize(), env.binaryFile().length());
        assertEquals(((S3FileObject)dest).getObjectMetadata().getSSEAlgorithm(), AES_256_SERVER_SIDE_ENCRYPTION);

        BufferedReader reader = new BufferedReader(new InputStreamReader(dest.getContent().getInputStream(), "US-ASCII"));
        try {
            assertEquals(reader.readLine(), env.binaryFile());
        } finally {
            reader.close();
        }
        dest.delete();
    }

    @Test(dependsOnMethods={"getSize"})
    public void download() throws IOException {
        FileObject typica = env.resolveFile("/test-place/backup.zip");
        File localCache =  File.createTempFile("vfs.", ".s3-test");

        // Copy from S3 to localfs
        FileOutputStream out = new FileOutputStream(localCache);
        IOUtils.copy(typica.getContent().getInputStream(), out);

        // Test that file sizes equals
        assertEquals(localCache.length(), typica.getContent().getSize());

        localCache.delete();
    }

    @Test(dependsOnMethods={"createFileOk", "createDirOk"})
    public void listChildren() throws FileSystemException {
        FileObject baseDir = vfs.resolveFile(dir, "list-children-test");
        baseDir.createFolder();

        for (int i=0; i<5; i++) {
            FileObject tmpFile = vfs.resolveFile(baseDir, i + ".tmp");
            tmpFile.createFile();
        }

        FileObject[] children = baseDir.getChildren();
        assertEquals(children.length, 5);
    }

    @Test(dependsOnMethods = {"createFileOk", "createDirOk", "uploadBigFile"})
    public void listChildrenRoot() throws FileSystemException {
        assertHasChildren(env.resolveFile("/"), "test-place", BIG_FILE, "acl"); // 'acl' came from ACL test
        assertHasChildren(env.resolveFile("/test-place/"), "backup.zip", dirName, encryptedFileName);
        assertHasChildren(env.resolveFile("/test-place"), "backup.zip", dirName, encryptedFileName);

        final FileObject destFile = env.resolveFile("/test-place-2");

        destFile.copyFrom(env.resolveFile("/test-place"), SELECT_ALL);

        assertHasChildren(destFile, "backup.zip", dirName, encryptedFileName);
    }

    @Test(dependsOnMethods={"createDirOk"})
    public void findFiles() throws FileSystemException {
        FileObject baseDir = vfs.resolveFile(dir, "find-tests");
        baseDir.createFolder();

        // Create files and dirs
        vfs.resolveFile(baseDir, "child-file.tmp").createFile();
        vfs.resolveFile(baseDir, "child-file2.tmp").createFile();
        vfs.resolveFile(baseDir, "child-dir").createFolder();
        vfs.resolveFile(baseDir, "child-dir/descendant.tmp").createFile();
        vfs.resolveFile(baseDir, "child-dir/descendant2.tmp").createFile();
        vfs.resolveFile(baseDir, "child-dir/descendant-dir").createFolder();

        FileObject[] files;
        files = baseDir.findFiles(Selectors.SELECT_CHILDREN);
        assertEquals(files.length, 3);
        files = baseDir.findFiles(Selectors.SELECT_FOLDERS);
        assertEquals(files.length, 3);
        files = baseDir.findFiles(Selectors.SELECT_FILES);
        assertEquals(files.length, 4);
        files = baseDir.findFiles(Selectors.EXCLUDE_SELF);
        assertEquals(files.length, 6);
    }

    @Test(dependsOnMethods={"createFileOk"})
    public void renameAndMove() throws FileSystemException {
        FileObject sourceFile = env.resolveFile("/test-place/%s", fileName);
        FileObject targetFile = env.resolveFile("/test-place/rename-target");

        assertTrue(sourceFile.exists());

        if (targetFile.exists()) {
            targetFile.delete();
        }

        assertFalse(targetFile.exists());
        assertFalse(sourceFile.canRenameTo(targetFile));

        sourceFile.moveTo(targetFile);

        assertTrue(targetFile.exists());
        assertFalse(sourceFile.exists());

        targetFile.moveTo(sourceFile);

        assertTrue(sourceFile.exists());
        assertFalse(targetFile.exists());

        try {
            sourceFile.moveTo(sourceFile);

            assertTrue(false); // Should block copy into itself
        } catch (FileSystemException ignored) {
        }
    }

    @Test(dependsOnMethods={"createFileOk", "createDirOk"})
    public void getType() throws FileSystemException {
        FileObject imagine = vfs.resolveFile(dir, "imagine-there-is-no-countries");

        assertEquals(imagine.getType(), FileType.IMAGINARY);
        assertEquals(dir.getType(), FileType.FOLDER);
        assertEquals(file.getType(), FileType.FILE);
    }

    @Test(dependsOnMethods={"createFileOk", "createDirOk"})
    public void getTypeAfterCopyToSubFolder() throws FileSystemException {
        FileObject dest = vfs.resolveFile(dir, "type-tests/sub1/sub2/backup.zip");

        // Copy data
        final File backupFile = new File(env.binaryFile());

        assertTrue(backupFile.exists(), "Backup file should exists");

        FileObject src = vfs.resolveFile(backupFile.getAbsolutePath());
        dest.copyFrom(src, Selectors.SELECT_SELF);

        assertTrue(dest.exists() && dest.getType().equals(FileType.FILE));

        FileObject sub1 = vfs.resolveFile(dir, "type-tests/sub1");
        assertTrue(sub1.exists());
        assertTrue(sub1.getType().equals(FileType.FOLDER));

        FileObject sub2 = vfs.resolveFile(dir, "type-tests/sub1/sub2");
        assertTrue(sub2.exists());
        assertTrue(sub2.getType().equals(FileType.FOLDER));
    }

    @Test(dependsOnMethods = {"createFileOk"})
    public void setEndpoint() throws FileSystemException, MalformedURLException {
        final S3FileSystemOptions options = new S3FileSystemOptions();

        FileObject endpointFile = env.resolveFile("/test-place/");

        // extract bucket endpoint from signed url
        IPublicUrlsGetter urlsGetter = (IPublicUrlsGetter) endpointFile.getFileOperations().getOperation(IPublicUrlsGetter.class);
        final String signedUrl = urlsGetter.getSignedUrl(60);
        // remove bucket name prefix from host to get endpoint
        String endpoint = new URL(signedUrl).getHost().replaceFirst(".*?\\.", "");
        // if default S3 endpoint, use alternate endpoint for that region just to make it interesting
        if (endpoint.equals("s3.amazonaws.com")) {
            endpoint = "s3-external-1.amazonaws.com";
        }

        options.setEndpoint(endpoint);

        endpointFile = env.resolveFile(options, "/test-place/");

        assertEquals(
                new S3FileSystemOptions(endpointFile.getFileSystem().getFileSystemOptions()).getEndpoint().orElse(""),
                endpoint
        );

        assertTrue(endpointFile.exists());
    }

    @Test(dependsOnMethods={"upload"})
    public void getContentType() throws FileSystemException {
        FileObject backup = env.resolveFile("/test-place/backup.zip");
        assertEquals(backup.getContent().getContentInfo().getContentType(), "application/zip");
    }

    @Test(dependsOnMethods={"upload"})
    public void getSize() throws FileSystemException {
        FileObject backup = env.resolveFile("/test-place/backup.zip");

        assertEquals(backup.getContent().getSize(), 996166);
    }

    @Test(dependsOnMethods={"upload"})
    public void getUrls() throws FileSystemException {
        FileObject backup = env.resolveFile("/test-place/backup.zip");

        assertTrue(backup.getFileOperations().hasOperation(IPublicUrlsGetter.class));

        IPublicUrlsGetter urlsGetter = (IPublicUrlsGetter) backup.getFileOperations().getOperation(IPublicUrlsGetter.class);

        assertEquals(urlsGetter.getHttpUrl(), "http://" + env.bucketName() + ".s3.amazonaws.com/test-place/backup.zip");
        assertTrue(urlsGetter.getPrivateUrl().endsWith("@" + env.bucketName() + "/test-place/backup.zip"));

        final String signedUrl = urlsGetter.getSignedUrl(60);

        assertTrue(
            signedUrl.matches("https://" + Pattern.quote(env.bucketName()) + "\\.s3.*?\\.amazonaws\\.com/test-place/backup\\.zip\\?.+"),
            signedUrl);
        assertTrue(signedUrl.contains("Signature="));
        assertTrue(signedUrl.contains("Expires="));
        assertTrue(signedUrl.contains("X-Amz-Credential="));
        assertTrue(signedUrl.contains("X-Amz-Signature="));
    }

    @Test(dependsOnMethods={"upload"})
    public void getMD5Hash() throws NoSuchAlgorithmException, IOException {
        FileObject backup = env.resolveFile("/test-place/backup.zip");

        assertTrue(backup.getFileOperations().hasOperation(IMD5HashGetter.class));

        IMD5HashGetter md5Getter = (IMD5HashGetter) backup.getFileOperations().getOperation(IMD5HashGetter.class);

        String md5Remote = md5Getter.getMD5Hash();

        Assert.assertNotNull(md5Remote);

        final File backupFile = new File(env.binaryFile());

        assertTrue(backupFile.exists(), "Backup file should exists");

        String md5Local = toHex(computeMD5Hash(new FileInputStream(backupFile)));

        assertEquals(md5Remote, md5Local, "Local and remote md5 should be equal");
    }

    @Test(dependsOnMethods = "upload")
    public void getLastModified() throws FileSystemException {
        FileObject backup = env.resolveFile("");

        assertEquals(backup.getContent().getLastModifiedTime(), 0L);
    }

    @Test(dependsOnMethods={"findFiles"})
	public void copyInsideBucket() throws FileSystemException {
        FileObject testsDir = vfs.resolveFile(dir, "find-tests");
        FileObject testsDirCopy = testsDir.getParent().resolveFile("find-tests-copy");
        testsDirCopy.copyFrom(testsDir, Selectors.SELECT_SELF_AND_CHILDREN);

        // Should have same number of files
        FileObject[] files = testsDir.findFiles(Selectors.SELECT_SELF_AND_CHILDREN);
        FileObject[] filesCopy = testsDirCopy.findFiles(Selectors.SELECT_SELF_AND_CHILDREN);
        assertEquals(files.length, filesCopy.length,
            Arrays.deepToString(files) + " vs. " + Arrays.deepToString(filesCopy));
	}

    @Test(dependsOnMethods={"findFiles"})
    public void copyAllToEncryptedInsideBucket() throws FileSystemException {
        final S3FileSystemOptions options = new S3FileSystemOptions();

        options.setServerSideEncryption(true);

        FileObject testsDir = vfs.resolveFile(dir, "find-tests");
        FileObject testsDirCopy = env.
                resolveFile(options, "/test-place/%s", dirName).
                resolveFile("find-tests-encrypted-copy");

        testsDirCopy.copyFrom(testsDir, SELECT_ALL);

        // Should have same number of files
        FileObject[] files = testsDir.findFiles(SELECT_ALL);
        FileObject[] filesCopy = testsDirCopy.findFiles(SELECT_ALL);
        assertEquals(files.length, filesCopy.length);

        for (int i = 0; i < files.length; i++) {
            if (files[i].getType() == FileType.FILE) {
                assertEquals(((S3FileObject)files[i]).getObjectMetadata().getSSEAlgorithm(), null);
                assertEquals(((S3FileObject)filesCopy[i]).getObjectMetadata().getSSEAlgorithm(), AES_256_SERVER_SIDE_ENCRYPTION);
            }
        }
    }

    @Test(dependsOnMethods={"findFiles", "download"})
    public void delete() throws FileSystemException {
        FileObject testsDir = vfs.resolveFile(dir, "find-tests");
        testsDir.delete(Selectors.EXCLUDE_SELF);

        // Only tests dir must remains
        FileObject[] files = testsDir.findFiles(SELECT_ALL);
        assertEquals(files.length, 1);
    }

    @Test(dependsOnMethods={"createFileOk"})
    public void localCacheMissing() throws IOException {
        FileObject dest = env.resolveFile("/test-place/output.txt");

        // Copy data
        try (OutputStream os = dest.getContent().getOutputStream()) {
            os.write(env.binaryFile().getBytes("US-ASCII"));
        }

        // verify data can be read
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(dest.getContent().getInputStream(), "US-ASCII"))) {
            assertEquals(reader.readLine(), env.binaryFile());
        }

        // delete all local cache files
        Path tempFile = Files.createTempFile("vfs.", ".s3");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempFile.getParent(), "vfs.*.s3")) {
            for (Path path : stream) {
                Files.delete(path);
            }
        }
        
        // verify data can still be read
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(dest.getContent().getInputStream(), "US-ASCII"))) {
            assertEquals(reader.readLine(), env.binaryFile());
        }
        
        dest.delete();
    }


    @AfterClass
    public void tearDown() throws FileSystemException {
        try {
            env.resolveFile("/" + BIG_FILE).delete();
            env.resolveFile("/test-place").delete(SELECT_ALL);
            env.resolveFile("/test-place-2").delete(SELECT_ALL);

            ((AbstractFileSystem) env.resolveFile("/").getFileSystem()).close();
        } catch (Exception ignored) {
        }
    }


    /**
     * Converts byte data to a Hex-encoded string.
     *
     * @param data data to hex encode.
     * @return hex-encoded string.
     */
    private String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte aData : data) {
            String hex = Integer.toHexString(aData);
            if (hex.length() == 1) {
                // Append leading zero.
                sb.append("0");
            } else if (hex.length() == 8) {
                // Remove ff prefix from negative numbers.
                hex = hex.substring(6);
            }
            sb.append(hex);
        }
        return sb.toString().toLowerCase(Locale.getDefault());
    }

    private byte[] computeMD5Hash(InputStream is) throws NoSuchAlgorithmException, IOException {
        BufferedInputStream bis = new BufferedInputStream(is);
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[16384];
            int bytesRead;
            while ((bytesRead = bis.read(buffer, 0, buffer.length)) != -1) {
                messageDigest.update(buffer, 0, bytesRead);
            }
            return messageDigest.digest();
        } finally {
            try {
                bis.close();
            } catch (Exception e) {
                System.err.println("Unable to close input stream of hash candidate: " + e);
            }
        }
    }
}
