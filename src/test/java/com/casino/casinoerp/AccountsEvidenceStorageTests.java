package com.casino.casinoerp;
import com.casino.casinoerp.service.AccountsEvidenceStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
class AccountsEvidenceStorageTests {
 @TempDir Path directory;
 byte[] image(String format)throws Exception{var b=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,3,java.awt.image.BufferedImage.TYPE_INT_RGB),format,b);return b.toByteArray();}
 @Test void pdfPngJpegAreStoredPrivatelyWithIntegrityChecks()throws Exception{
  var storage=new AccountsEvidenceStorage(directory.toRealPath().toString());
  var pdfBytes=new java.io.ByteArrayOutputStream();try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()){pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(pdfBytes);}
  for(var entry:java.util.Map.of("application/pdf",pdfBytes.toByteArray(),"image/png",image("png"),"image/jpeg",image("jpeg")).entrySet()){
   var key=storage.store(entry.getValue(),entry.getKey());assertThat(storage.read(key,AccountsEvidenceStorage.checksum(entry.getValue()),entry.getValue().length)).isEqualTo(entry.getValue());
   assertThat(Files.getPosixFilePermissions(directory.resolve(key.toString()))).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
   assertThatThrownBy(()->storage.read(key,"wrong",entry.getValue().length)).hasMessageContaining("integrity");
  }
 }
 @Test void rejectsDeclaredTypeMismatchTruncationEmptyOversizeAndUnavailableStorage()throws Exception {
  var s=new AccountsEvidenceStorage(directory.toRealPath().toString());var png=image("png");
  assertThatThrownBy(()->s.store(png,"image/jpeg")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->s.store(new byte[0],"application/pdf")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->s.store(new byte[AccountsEvidenceStorage.MAX_BYTES+1],"image/png")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->s.store("%PDF-invalid".getBytes(),"application/pdf")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->s.store(png,"text/html")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new AccountsEvidenceStorage("").store(png,"image/png")).hasMessageContaining("not configured");
  Path file=directory.resolve("not-directory");Files.writeString(file,"file");assertThatThrownBy(()->new AccountsEvidenceStorage(file.toString()).store(png,"image/png")).isInstanceOf(IllegalStateException.class);
 }
 @Test void rejectsRepositoryPublicRelativeAndSymbolicStorage()throws Exception {
  var bytes=image("png");Path repo=directory.resolve("repo");Files.createDirectories(repo.resolve(".git"));Path link=directory.resolve("link");Files.createSymbolicLink(link,repo);
  for(String path:java.util.List.of("relative",repo.resolve("evidence").toString(),directory.resolve("public/uploads").toString(),link.resolve("evidence").toString()))assertThatThrownBy(()->new AccountsEvidenceStorage(path).store(bytes,"image/png")).isInstanceOf(IllegalStateException.class);
 }
 @Test void truncatedRealDocumentsAreNotAccepted()throws Exception {
  var s=new AccountsEvidenceStorage(directory.toRealPath().toString());
  for(String format:java.util.List.of("png","jpeg")) {byte[] bytes=image(format);assertThatThrownBy(()->s.validate(java.util.Arrays.copyOf(bytes,bytes.length-2),"image/"+format)).isInstanceOf(IllegalArgumentException.class);}
  var out=new java.io.ByteArrayOutputStream();try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()){pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(out);}
  byte[] bytes=out.toByteArray();assertThatThrownBy(()->s.validate(java.util.Arrays.copyOf(bytes,bytes.length-12),"application/pdf")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void rejectsUnsafeFilenamesWithoutInterpretingThemAsPaths(){
  for(String name:java.util.List.of("../invoice.pdf","/tmp/invoice.pdf","C:\\invoice.pdf","evil\r\nX-Test: injected.pdf",".",".."))assertThatThrownBy(()->AccountsEvidenceStorage.filename(name)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void privateRootIsRequiredAndSymlinkOrChangedBlobCannotBeRead()throws Exception {
  Path root=directory.toRealPath().resolve("private");var s=new AccountsEvidenceStorage(root.toString());byte[] bytes=image("png");var key=s.store(bytes,"image/png");String checksum=AccountsEvidenceStorage.checksum(bytes);
  Files.delete(root.resolve(key.toString()));Files.createSymbolicLink(root.resolve(key.toString()),directory.resolve("outside"));Files.write(directory.resolve("outside"),bytes);
  assertThatThrownBy(()->s.read(key,checksum,bytes.length)).isInstanceOf(IllegalStateException.class);
  Files.delete(root.resolve(key.toString()));bytes[bytes.length-1]^=1;Files.write(root.resolve(key.toString()),bytes);assertThatThrownBy(()->s.read(key,checksum,bytes.length)).isInstanceOf(IllegalStateException.class);
  Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));assertThatThrownBy(()->s.store(image("png"),"image/png")).isInstanceOf(IllegalStateException.class);
  assertThat(Files.getPosixFilePermissions(root)).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
 }
 @Test void pngCrcCorruptionAndOversizedDimensionsAreRejected()throws Exception {
  byte[] png=image("png");png[29]^=1;var s=new AccountsEvidenceStorage(directory.toRealPath().toString());assertThatThrownBy(()->s.validate(png,"image/png")).isInstanceOf(IllegalArgumentException.class);
  byte[] bomb=image("png");java.nio.ByteBuffer.wrap(bomb,16,4).putInt(100000);java.nio.ByteBuffer.wrap(bomb,20,4).putInt(100000);var crc=new java.util.zip.CRC32();crc.update(bomb,12,17);java.nio.ByteBuffer.wrap(bomb,29,4).putInt((int)crc.getValue());assertThatThrownBy(()->s.validate(bomb,"image/png")).isInstanceOf(IllegalArgumentException.class);
 }
}
