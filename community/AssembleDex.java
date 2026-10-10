import brut.androlib.smali.SmaliBuilder;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import java.nio.file.Files;
import java.nio.file.Path;

class AssembleDex {
  public static void main(String[] arguments) throws Exception {
    var dex = new DexBuilder(new Opcodes(30));
    var assembler = new SmaliBuilder(30);
    int count = 0;
    try (var paths = Files.walk(Path.of(arguments[0]))) {
      for (var file : paths.filter(path -> path.toString().endsWith(".smali")).sorted().toList()) {
        if (!assembler.buildFile(file.toFile(), dex)) throw new IllegalStateException("Invalid smali: " + file);
        count++;
      }
    }
    dex.writeTo(new FileDataStore(Path.of(arguments[1]).toFile()));
    System.out.println("Assembled classes: " + count);
  }
}
