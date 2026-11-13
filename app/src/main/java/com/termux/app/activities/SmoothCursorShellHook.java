package com.termux.app.activities;

import android.content.Context;
import android.content.SharedPreferences;

import com.termux.shared.termux.TermuxConstants;
import com.termux.view.SmoothCursorEffects;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

/** Installs the single shared command-event bridge used by error, pulse and rule effects. */
public final class SmoothCursorShellHook {
    public static final String EXTRA_SHELL = "smooth_cursor_shell_name";

    public enum Result { UNCHANGED, INSTALLED, RESTORED, REMOVED, UNSUPPORTED, FAILED }

    private SmoothCursorShellHook() { }

    public static boolean isSupported(String shellName) {
        return normalize(shellName) != null;
    }

    public static String detectShellForPid(int pid) {
        if (pid < 1) return detectInstalledDefaultShell();
        Queue<Integer> pending = new ArrayDeque<>();
        pending.add(pid);
        int visited = 0;
        while (!pending.isEmpty() && visited++ < 20) {
            int current = pending.remove();
            String commandLine = readProc(new File("/proc/" + current + "/cmdline"));
            String first = commandLine == null ? null : commandLine.split("\u0000", -1)[0];
            String normalized = normalize(first);
            if (normalized != null) return normalized;

            String environment = readProc(new File("/proc/" + current + "/environ"));
            String shell = valueFromEnvironment(environment, "SHELL");
            normalized = normalize(shell);
            if (normalized != null) return normalized;

            String children = readProc(new File("/proc/" + current + "/task/" + current + "/children"));
            if (children != null) {
                for (String child : children.trim().split("\\s+")) {
                    try { pending.add(Integer.parseInt(child)); } catch (NumberFormatException ignored) { }
                }
            }
        }
        return detectInstalledDefaultShell();
    }

    private static String detectInstalledDefaultShell() {
        File home = TermuxConstants.TERMUX_HOME_DIR;
        if (new File(home, ".config/nushell/config.nu").isFile()) return "nushell";
        if (new File(home, ".tcshrc").isFile() || new File(home, ".cshrc").isFile()) return "tcsh";
        if (new File(home, ".config/fish/config.fish").isFile()) return "fish";
        if (new File(home, ".zshrc").isFile()) return "zsh";
        String[] shells = {"bash", "zsh", "fish", "tcsh", "nu"};
        for (String candidate : shells) {
            if (new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, candidate).canExecute())
                return normalize(candidate);
        }
        return null;
    }

    private static String valueFromEnvironment(String environment, String key) {
        if (environment == null) return null;
        for (String entry : environment.split("\u0000"))
            if (entry.startsWith(key + "=")) return entry.substring(key.length() + 1);
        return null;
    }

    private static String readProc(File file) {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ignored) { return null; }
    }

    private static String normalize(String pathOrName) {
        if (pathOrName == null) return null;
        String name = pathOrName.trim();
        int zero = name.indexOf('\u0000');
        if (zero >= 0) name = name.substring(0, zero);
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        while (name.startsWith("-")) name = name.substring(1);
        name = name.toLowerCase(Locale.ROOT);
        if ("bash".equals(name)) return "bash";
        if ("zsh".equals(name)) return "zsh";
        if ("fish".equals(name)) return "fish";
        if ("tcsh".equals(name) || "csh".equals(name)) return "tcsh";
        if ("nu".equals(name) || "nushell".equals(name)) return "nushell";
        return null;
    }

    public static Result sync(Context context, SharedPreferences prefs, String shellName) {
        boolean enabled = SmoothCursorEffects.isCommandHookNeeded(prefs);
        String shell = normalize(shellName);
        if (shell == null) return enabled ? Result.UNSUPPORTED : Result.UNCHANGED;
        File home = TermuxConstants.TERMUX_HOME_DIR;
        File rc = rcFile(home, shell);
        String line = sourceLine(home, shell);
        File script = helperFile(home, shell);
        try {
            if (!enabled) {
                if (!rc.isFile()) return Result.UNCHANGED;
                String old = readText(rc);
                String updated = removeLine(old, line);
                if (old.equals(updated)) return Result.UNCHANGED;
                writeText(rc, updated);
                return Result.REMOVED;
            }

            File parent = script.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())
                return Result.FAILED;
            writeText(script, hookScript(shell));
            script.setReadable(true, false);
            script.setWritable(true, true);

            String old = rc.isFile() ? readText(rc) : "";
            if (containsLine(old, line)) return Result.UNCHANGED;
            boolean hadInstalled = prefs.getBoolean(SmoothCursorEffects.K_HOOK_EVER_INSTALLED, false);
            String updated = appendLine(old, line);
            File rcParent = rc.getParentFile();
            if (rcParent != null && !rcParent.isDirectory() && !rcParent.mkdirs() && !rcParent.isDirectory())
                return Result.FAILED;
            writeText(rc, updated);
            prefs.edit().putBoolean(SmoothCursorEffects.K_HOOK_EVER_INSTALLED, true).apply();
            return hadInstalled ? Result.RESTORED : Result.INSTALLED;
        } catch (IOException | SecurityException e) {
            return Result.FAILED;
        }
    }

    private static File rcFile(File home, String shell) {
        if ("bash".equals(shell)) return new File(home, ".bashrc");
        if ("zsh".equals(shell)) return new File(home, ".zshrc");
        if ("fish".equals(shell)) return new File(home, ".config/fish/config.fish");
        if ("tcsh".equals(shell)) return new File(home, ".tcshrc");
        return new File(home, ".config/nushell/config.nu");
    }

    private static File helperFile(File home, String shell) {
        String extension = "nushell".equals(shell) ? "nu" : shell;
        return new File(home, ".config/smoothcursor/command-hook." + extension);
    }

    private static String sourceLine(File home, String shell) {
        if ("nushell".equals(shell))
            return "source ~/.config/smoothcursor/command-hook.nu # smoothcursor:command-hook:v1";
        String path = "$HOME/.config/smoothcursor/command-hook." + shell;
        return "source \"" + path + "\" # smoothcursor:command-hook:v1";
    }

    private static boolean containsLine(String text, String line) {
        for (String item : text.split("\\r?\\n", -1)) if (line.equals(item.trim())) return true;
        return false;
    }

    private static String appendLine(String text, String line) {
        if (text.isEmpty()) return line + "\n";
        return text + (text.endsWith("\n") ? "" : "\n") + line + "\n";
    }

    private static String removeLine(String text, String line) {
        String[] lines = text.split("\\r?\\n", -1);
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            if (line.equals(lines[i].trim())) continue;
            if (result.length() > 0) result.append('\n');
            result.append(lines[i]);
        }
        return result.toString();
    }

    private static String readText(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())
            throw new IOException("Cannot create " + parent);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }

    private static String hookScript(String shell) {
        switch (shell) {
            case "bash": return BASH_HOOK;
            case "zsh": return ZSH_HOOK;
            case "fish": return FISH_HOOK;
            case "tcsh": return TCSH_HOOK;
            default: return NUSHELL_HOOK;
        }
    }

    private static final String BASH_HOOK = String.join("\n",
            "case $- in *i*) ;; *) return ;; esac" ,
            "[ -n \"${__SMOOTH_CURSOR_HOOK_LOADED-}\" ] && return" ,
            "__SMOOTH_CURSOR_HOOK_LOADED=1" ,
            "__SMOOTH_CURSOR_LAST_HISTCMD=${HISTCMD:-0}" ,
            "__smoothcursor_emit() {" ,
            "    local kind=\"$1\" status=\"$2\" command=\"$3\" encoded" ,
            "    encoded=$(printf '%s' \"$command\" | base64 2>/dev/null | tr -d '\\r\\n' | head -c 6000)" ,
            "    printf '\\033]777;SC;%s;%s;%s\\007' \"$kind\" \"$status\" \"$encoded\"" ,
            "}" ,
            "__smoothcursor_preexec() {" ,
            "    local command" ,
            "    command=$(fc -ln -1 2>/dev/null)" ,
            "    [ -n \"$command\" ] && __smoothcursor_emit B 0 \"$command\"" ,
            "}" ,
            "__smoothcursor_precmd() {" ,
            "    local status=$? command current_histcmd=${HISTCMD:-0}" ,
            "    if [ \"$current_histcmd\" != \"$__SMOOTH_CURSOR_LAST_HISTCMD\" ]; then" ,
            "        command=$(fc -ln -1 2>/dev/null)" ,
            "        [ -n \"$command\" ] && __smoothcursor_emit E \"$status\" \"$command\"" ,
            "    fi" ,
            "    __SMOOTH_CURSOR_LAST_HISTCMD=$current_histcmd" ,
            "    return \"$status\"" ,
            "}" ,
            "PS0=\"${PS0-}\\$(__smoothcursor_preexec)\"" ,
            "if declare -p PROMPT_COMMAND 2>/dev/null | grep -q 'declare -a'; then" ,
            "    PROMPT_COMMAND=(__smoothcursor_precmd \"${PROMPT_COMMAND[@]}\")" ,
            "else" ,
            "    PROMPT_COMMAND=\"__smoothcursor_precmd${PROMPT_COMMAND:+; $PROMPT_COMMAND}\"" ,
            "fi") + "\n";

    private static final String ZSH_HOOK = String.join("\n",
            "[[ -o interactive ]] || return" ,
            "(( ${+functions[__smoothcursor_preexec]} )) && return" ,
            "__smoothcursor_emit() {" ,
            "    local kind=\"$1\" status=\"$2\" command=\"$3\" encoded" ,
            "    encoded=$(printf '%s' \"$command\" | base64 2>/dev/null | tr -d '\\r\\n' | head -c 6000)" ,
            "    printf '\\033]777;SC;%s;%s;%s\\007' \"$kind\" \"$status\" \"$encoded\"" ,
            "}" ,
            "__smoothcursor_preexec() { __SMOOTH_CURSOR_COMMAND=\"$1\"; __smoothcursor_emit B 0 \"$1\" }" ,
            "__smoothcursor_precmd() {" ,
            "    local status=$? command=\"${__SMOOTH_CURSOR_COMMAND-}\"" ,
            "    [[ -n \"$command\" ]] && __smoothcursor_emit E \"$status\" \"$command\"" ,
            "    __SMOOTH_CURSOR_COMMAND=\"\"" ,
            "    return \"$status\"" ,
            "}" ,
            "autoload -Uz add-zsh-hook" ,
            "add-zsh-hook preexec __smoothcursor_preexec" ,
            "add-zsh-hook precmd __smoothcursor_precmd") + "\n";

    private static final String FISH_HOOK = String.join("\n",
            "functions -q __smoothcursor_emit; and return" ,
            "function __smoothcursor_emit" ,
            "    set -l kind $argv[1]; set -l status_code $argv[2]; set -l command_text $argv[3]" ,
            "    set -l encoded (printf '%s' \"$command_text\" | base64 2>/dev/null | tr -d '\\r\\n' | head -c 6000)" ,
            "    printf '\\e]777;SC;%s;%s;%s\\a' \"$kind\" \"$status_code\" \"$encoded\"" ,
            "end" ,
            "function __smoothcursor_preexec --on-event fish_preexec" ,
            "    __smoothcursor_emit B 0 \"$argv[1]\"" ,
            "end" ,
            "function __smoothcursor_postexec --on-event fish_postexec" ,
            "    set -l status_code $status" ,
            "    __smoothcursor_emit E $status_code \"$argv[1]\"" ,
            "end") + "\n";

    private static final String TCSH_HOOK = String.join("\n",
            "if (! $?__SMOOTH_CURSOR_HOOK_LOADED) then" ,
            "    set __SMOOTH_CURSOR_HOOK_LOADED" ,
            "    alias __smoothcursor_encode 'echo -n \"\\!:1\" | base64 | tr -d \"\\r\\n\"'" ,
            "    alias postcmd 'set __sc_pending; set __sc_cmd=`history -h 1`; set __sc_encoded=`echo -n \"$__sc_cmd\" | base64 | tr -d \"\\r\\n\" | head -c 6000`; printf \"\\033]777;SC;B;0;%s\\007\" \"$__sc_encoded\"'" ,
            "    alias __smoothcursor_finish 'set __sc_cmd=`history -h 1`; set __sc_encoded=`echo -n \"$__sc_cmd\" | base64 | tr -d \"\\r\\n\" | head -c 6000`; printf \"\\033]777;SC;E;%s;%s\\007\" \"$__sc_status\" \"$__sc_encoded\"; unset __sc_pending'" ,
            "    alias precmd 'set __sc_status=$status; if ($?__sc_pending) __smoothcursor_finish'" ,
            "endif") + "\n";

    private static final String NUSHELL_HOOK = String.join("\n",
            "let hooks = ($env.config.hooks? | default {})" ,
            "let pre_execution = ($hooks.pre_execution? | default [])" ,
            "let pre_prompt = ($hooks.pre_prompt? | default [])" ,
            "let smoothcursor_pre = {||" ,
            "    let command_text = (commandline)" ,
            "    $env.SMOOTH_CURSOR_COMMAND = $command_text" ,
            "    let encoded = ($command_text | encode base64 | str replace -a \"\\n\" \"\")" ,
            "    print -n $\"(char esc)]777;SC;B;0;($encoded)(char bel)\"" ,
            "}" ,
            "let smoothcursor_post = {||" ,
            "    let command_text = ($env.SMOOTH_CURSOR_COMMAND? | default '')" ,
            "    if ($command_text | is-not-empty) {" ,
            "        let status_code = ($env.LAST_EXIT_CODE? | default 0)" ,
            "        let encoded = ($command_text | encode base64 | str replace -a \"\\n\" \"\")" ,
            "        print -n $\"(char esc)]777;SC;E;($status_code);($encoded)(char bel)\"" ,
            "        $env.SMOOTH_CURSOR_COMMAND = ''" ,
            "    }" ,
            "}" ,
            "$env.config = ($env.config | upsert hooks.pre_execution ($pre_execution | append $smoothcursor_pre))" ,
            "$env.config = ($env.config | upsert hooks.pre_prompt ($pre_prompt | append $smoothcursor_post))") + "\n";
}
