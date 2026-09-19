package com.casino.casinoerp.service;

import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;

/** Private immutable blobs. DB rollback can leave an orphan, never accepted metadata for an incomplete file. */
@Component
public class AccountsEvidenceStorage {
    public static final int MAX_BYTES=5*1024*1024;
    private final String configuredRoot;
    public AccountsEvidenceStorage(@Value("${accounts.evidence.directory:}") String root) {this.configuredRoot=root;}
    private Path root() throws IOException {
        if(configuredRoot==null || configuredRoot.isBlank())throw new IllegalStateException("Accounts private evidence storage is not configured.");
        Path p=Path.of(configuredRoot);
        if(!p.isAbsolute())throw new IllegalStateException("Accounts storage must be an absolute private directory.");
        p=p.normalize();
        for(Path a=p;a!=null;a=a.getParent()) {
            if(Files.isSymbolicLink(a) || Files.exists(a.resolve(".git")) || Set.of("public","static","webapps","www","htdocs").contains(String.valueOf(a.getFileName()).toLowerCase(Locale.ROOT)))
                throw new IllegalStateException("Accounts storage must be outside repositories and public roots, without symbolic links.");
        }
        Files.createDirectories(p,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Private evidence directory unavailable.");
        if(!PosixFilePermissions.fromString("rwx------").containsAll(Files.getPosixFilePermissions(p)))throw new IOException("Evidence directory must already be private.");
        return p;
    }
    public static String filename(String name) {
        if(name==null || name.isBlank() || name.length()>200 || name.equals(".") || name.equals("..") || name.indexOf('/')>=0 || name.indexOf('\\')>=0 || name.chars().anyMatch(c->c<32||c==127))
            throw new IllegalArgumentException("Evidence requires a plain filename without paths or control characters.");
        return name;
    }
    public static String checksum(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception e) {throw new IllegalStateException(e);}
    }
    public void validate(byte[] bytes,String type) {
        if(bytes==null || bytes.length==0 || bytes.length>MAX_BYTES)throw new IllegalArgumentException("Evidence must be between 1 byte and 5 MiB (5,242,880 bytes).");
        try {
            if("application/pdf".equals(type)) {
                if(!new String(bytes,0,Math.min(5,bytes.length),java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new IOException();
                String tail=new String(bytes,Math.max(0,bytes.length-1024),Math.min(bytes.length,1024),java.nio.charset.StandardCharsets.ISO_8859_1);
                if(!tail.stripTrailing().endsWith("%%EOF"))throw new IOException();
                var parser=new PDFParser(new RandomAccessReadBuffer(bytes));
                try(var pdf=parser.parse(false)) {if(pdf.isEncrypted() || pdf.getNumberOfPages()<1 || pdf.getNumberOfPages()>2000)throw new IOException();}
            } else if("image/jpeg".equals(type)||"image/png".equals(type)) {
                if("image/png".equals(type))validatePngChunks(bytes);
                else if(bytes.length<4 || (bytes[bytes.length-2]&255)!=255 || (bytes[bytes.length-1]&255)!=217)throw new IOException();
                try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();
                    var reader=readers.next();try {
                        reader.setInput(input,true,true);
                        boolean[] warning={false};reader.addIIOReadWarningListener((source,message)->warning[0]=true);
                        String format=reader.getFormatName();
                        if(!("image/png".equals(type)?"png".equalsIgnoreCase(format):"jpeg".equalsIgnoreCase(format)))throw new IOException();
                        if((long)reader.getWidth(0)*reader.getHeight(0)>40_000_000 || reader.read(0)==null || warning[0])throw new IOException();
                    } finally {reader.dispose();}
                }
            } else throw new IOException();
        } catch(IOException|RuntimeException e) {throw new IllegalArgumentException("Evidence must be a readable, unencrypted PDF, JPEG or PNG matching its declared type.");}
    }
    private static void validatePngChunks(byte[] bytes)throws IOException {
        byte[] signature={(byte)137,80,78,71,13,10,26,10};
        if(bytes.length<20 || !Arrays.equals(signature,Arrays.copyOf(bytes,8)))throw new IOException();
        int offset=8;boolean end=false;
        while(offset<bytes.length) {
            if(bytes.length-offset<12)throw new IOException();
            int length=java.nio.ByteBuffer.wrap(bytes,offset,4).getInt();
            if(length<0 || length>bytes.length-offset-12)throw new IOException();
            var crc=new java.util.zip.CRC32();crc.update(bytes,offset+4,length+4);
            long expected=Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes,offset+8+length,4).getInt());
            if(crc.getValue()!=expected)throw new IOException();
            String type=new String(bytes,offset+4,4,java.nio.charset.StandardCharsets.US_ASCII);
            offset+=length+12;
            if(type.equals("IEND")) {if(length!=0 || offset!=bytes.length)throw new IOException();end=true;break;}
        }
        if(!end)throw new IOException();
    }
    public UUID store(byte[] bytes,String type) {
        validate(bytes,type);Path temporary=null;
        try {
            Path root=root();temporary=Files.createTempFile(root,"upload-",".incomplete",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try(var out=new FileOutputStream(temporary.toFile())) {out.write(bytes);out.getFD().sync();}
            UUID key=UUID.randomUUID();Files.move(temporary,root.resolve(key.toString()),StandardCopyOption.ATOMIC_MOVE);return key;
        } catch(IOException e) {throw new IllegalStateException("Evidence could not be stored; the upload was not accepted.",e);}
        finally {if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){/* Incomplete file is never referenced by accepted evidence. */}}
    }
    public byte[] read(UUID key,String expectedChecksum,int size) {
        try {
            Path p=root().resolve(key.toString());
            if(size<1||size>MAX_BYTES||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)!=size)throw new IOException();
            try(var input=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes=input.readNBytes(MAX_BYTES+1);
                if(bytes.length!=size || !checksum(bytes).equals(expectedChecksum))throw new IOException();return bytes;
            }
        } catch(IOException e) {throw new IllegalStateException("Stored evidence is unavailable or failed its integrity check.");}
    }
}
