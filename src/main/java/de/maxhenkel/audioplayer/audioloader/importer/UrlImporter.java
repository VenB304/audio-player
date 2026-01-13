package de.maxhenkel.audioplayer.audioloader.importer;

import de.maxhenkel.audioplayer.api.importer.AudioImportInfo;
import de.maxhenkel.audioplayer.api.importer.AudioImporter;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

public class UrlImporter implements AudioImporter {

    public static final String USER_AGENT = "AudioPlayer";

    private final UUID soundId;
    private final String urlString;
    private URL url;

    public UrlImporter(String url) {
        this.soundId = UUID.randomUUID();
        this.urlString = url;
    }

    @Override
    public AudioImportInfo onPreprocess(@Nullable ServerPlayer player) throws Exception {
        url = new URI(urlString).toURL();
        return new AudioImportInfo(soundId, getFileNameFromUrl(url.toString()));
    }

    @Nullable
    public static String getFileNameFromUrl(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            return null;
        }
        return name;
    }

    @Override
    public byte[] onProcess(@Nullable ServerPlayer player) throws Exception {
        if (de.maxhenkel.audioplayer.transcode.FFmpegTranscodeService.instance().needsTranscoding(urlString)) {
            if (player != null) {
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("Non-standard audio detected. Transcoding..."));
            }

            java.nio.file.Path tempFile;
            try {
                // Try to create with restricted permissions (Unix/Linux/Mac)
                java.util.Set<java.nio.file.attribute.PosixFilePermission> nioPermissions = java.nio.file.attribute.PosixFilePermissions
                        .fromString("rw-------");
                java.nio.file.attribute.FileAttribute<java.util.Set<java.nio.file.attribute.PosixFilePermission>> fileAttributes = java.nio.file.attribute.PosixFilePermissions
                        .asFileAttribute(nioPermissions);
                tempFile = java.nio.file.Files.createTempFile("audioplayer_transcode", ".mp3", fileAttributes);
            } catch (UnsupportedOperationException e) {
                // Fallback for Windows or systems without POSIX permission support
                tempFile = java.nio.file.Files.createTempFile("audioplayer_transcode", ".mp3");
            }

            try {
                de.maxhenkel.audioplayer.transcode.FFmpegTranscodeService.instance().process(urlString, tempFile);
                return java.nio.file.Files.readAllBytes(tempFile);
            } finally {
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    // Start a new thread to delete later or just log?
                    // For now, logging to console might be too noisy if it's a lock issue,
                    // but we should at least not crash the main thread.
                    de.maxhenkel.audioplayer.AudioPlayerMod.LOGGER.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.connect();
        try (InputStream is = connection.getInputStream()) {
            return is.readAllBytes();
        }
    }

    @Override
    public void onPostprocess(@Nullable ServerPlayer player) throws Exception {

    }

    @Override
    public String getHandlerName() {
        return "url";
    }

}
