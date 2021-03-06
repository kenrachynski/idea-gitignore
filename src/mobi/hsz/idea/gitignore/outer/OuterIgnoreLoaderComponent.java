/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package mobi.hsz.idea.gitignore.outer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.config.GitVcsApplicationSettings;
import mobi.hsz.idea.gitignore.file.type.IgnoreFileType;
import mobi.hsz.idea.gitignore.lang.IgnoreLanguage;
import mobi.hsz.idea.gitignore.lang.kind.FossilLanguage;
import mobi.hsz.idea.gitignore.lang.kind.GitLanguage;
import mobi.hsz.idea.gitignore.settings.IgnoreSettings;
import mobi.hsz.idea.gitignore.util.ProcessWithTimeout;
import mobi.hsz.idea.gitignore.util.Utils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

import static mobi.hsz.idea.gitignore.settings.IgnoreSettings.KEY;

/**
 * Component loader for outer ignore files editor.
 *
 * @author Jakub Chrzanowski <jakub@hsz.mobi>
 * @since 1.1
 */
public class OuterIgnoreLoaderComponent extends AbstractProjectComponent {
    /** Current project. */
    private final Project project;
    
    /** Outer files map. */
    private HashMap<IgnoreLanguage, VirtualFile> outerFile = ContainerUtil.newHashMap();

    /**
     * Returns {@link OuterIgnoreLoaderComponent} service instance.
     *
     * @param project current project
     * @return {@link OuterIgnoreLoaderComponent instance}
     */
    public static OuterIgnoreLoaderComponent getInstance(@NotNull final Project project) {
        return project.getComponent(OuterIgnoreLoaderComponent.class);
    }
    
    /** Constructor. */
    public OuterIgnoreLoaderComponent(@NotNull final Project project) {
        super(project);
        this.project = project;
    }

    /**
     * Returns component name.
     *
     * @return component name
     */
    @Override
    @NonNls
    @NotNull
    public String getComponentName() {
        return "IgnoreOuterComponent";
    }

    /** Initializes component.*/
    @Override
    public void initComponent() {
        myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new IgnoreEditorManagerListener(project));
    }

    @Override
    public void projectOpened() {
        // Outer file for {@link GitLanguage}
        if (Utils.isGitPluginEnabled()) {
            final String bin = GitVcsApplicationSettings.getInstance().getPathToGit();
            if (StringUtil.isNotEmpty(bin)) {
                try {
                    Process pr = Runtime.getRuntime().exec(bin + " config --global core.excludesfile");
                    pr.waitFor();

                    ProcessWithTimeout processWithTimeout = new ProcessWithTimeout(pr);
                    int exitCode = processWithTimeout.waitForProcess(3000);
                    if (exitCode == Integer.MIN_VALUE) {
                        pr.destroy();
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(pr.getInputStream()));
                    String path = Utils.resolveUserDir(reader.readLine());
                    if (StringUtil.isNotEmpty(path)) {
                        outerFile.put(GitLanguage.INSTANCE, VfsUtil.findFileByIoFile(new File(path), true));
                    }
                } catch (IOException ignored) {
                } catch (InterruptedException ignored) {
                }
            }
        }

        // Outer file for {@link FossilLanguage}
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
            outerFile.put(FossilLanguage.INSTANCE, baseDir.findFileByRelativePath("./.fossil-settings/ignore-glob"));
        }
    }

    /**
     * Returns outer file for the given {@link IgnoreLanguage}.
     * 
     * @param language of the outer file
     * @return outer file
     */
    @Nullable
    public VirtualFile getOuterFile(@NotNull IgnoreLanguage language) {
        return outerFile.get(language);
    }

    /**
     * Listener for ignore editor manager.
     */
    private static class IgnoreEditorManagerListener extends FileEditorManagerAdapter {
        /** Current project. */
        private final Project project;

        /** Constructor. */
        public IgnoreEditorManagerListener(@NotNull final Project project) {
            this.project = project;
        }

        /**
         * Handles file opening event and attaches outer ignore component.
         *
         * @param source editor manager
         * @param file current file
         */
        @Override
        public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
            FileType fileType = file.getFileType();
            if (!(fileType instanceof IgnoreFileType) || !IgnoreSettings.getInstance().isOuterIgnoreRules()) {
                return;
            }

            IgnoreLanguage language = ((IgnoreFileType) fileType).getIgnoreLanguage();
            if (!language.isEnabled()) {
                return;
            }

            VirtualFile outerFile = language.getOuterFile(project);
            if (outerFile == null || outerFile.equals(file)) {
                return;
            }

            for (final FileEditor fileEditor : source.getEditors(file)) {
                if (fileEditor instanceof TextEditor) {
                    final OuterIgnoreWrapper wrapper = new OuterIgnoreWrapper(project, outerFile);
                    final JComponent c = wrapper.getComponent();
                    source.addBottomComponent(fileEditor, c);

                    IgnoreSettings.getInstance().addListener(new IgnoreSettings.Listener() {
                        @Override
                        public void onChange(@NotNull KEY key, Object value) {
                            if (KEY.OUTER_IGNORE_RULES.equals(key)) {
                                c.setVisible((Boolean) value);
                            }
                        }
                    });

                    Disposer.register(fileEditor, wrapper);
                    Disposer.register(fileEditor, new Disposable() {
                        @Override
                        public void dispose() {
                            source.removeBottomComponent(fileEditor, c);
                        }
                    });
                }
            }
        }
    }
}