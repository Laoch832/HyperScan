import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import javax.tools.ToolProvider;
class CompileJava {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of(args[1]));
        var options = new ArrayList<>(Arrays.asList("-source", "8", "-target", "8", "-Xlint:-options", "-classpath", args[0], "-d", args[1]));
        options.addAll(Arrays.asList(args).subList(2, args.length));
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, options.toArray(new String[0]));
        if (result != 0) throw new IllegalStateException("Compiler exit " + result);
    }
}
