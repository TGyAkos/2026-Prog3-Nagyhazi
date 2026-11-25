package hu.bme.prog3.factcalc.service;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads, scales, and caches icons asynchronously from local file paths,
 * classpath resources, or HTTP/HTTPS URLs, returning a clean placeholder immediately.
 */
public class IconCacheService {
    private static final int DEFAULT_SIZE = 24;

    private final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();
    private final Set<String> loadingKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, CopyOnWriteArrayList<Runnable>> pendingCallbacks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "icon-loader");
        t.setDaemon(true);
        return t;
    });

    private final ImageIcon placeholder24 = createPlaceholder(DEFAULT_SIZE);

    /**
     * Retrieves a cached 24x24 icon or schedules asynchronous loading and returns a placeholder immediately.
     *
     * @param pathOrUrl file path, classpath URI, or HTTP/HTTPS URL of the image
     * @param onLoaded  optional EDT callback invoked once loading completes
     * @return the cached icon or a fallback placeholder icon
     */
    public Icon getIcon(String pathOrUrl, Runnable onLoaded) {
        return getIcon(pathOrUrl, DEFAULT_SIZE, onLoaded);
    }

    /**
     * Retrieves a cached square icon at the given pixel size, or schedules asynchronous loading.
     *
     * @param pathOrUrl file path, classpath URI, or HTTP/HTTPS URL of the image
     * @param size      target width and height in pixels
     * @param onLoaded  optional EDT callback invoked once loading completes
     * @return the cached icon or a fallback placeholder icon
     */
    public Icon getIcon(String pathOrUrl, int size, Runnable onLoaded) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return size == DEFAULT_SIZE ? placeholder24 : createPlaceholder(size);
        }
        String key = pathOrUrl.trim() + "@" + size;
        ImageIcon cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        if (onLoaded != null) {
            pendingCallbacks.computeIfAbsent(key, _ -> new CopyOnWriteArrayList<>()).add(onLoaded);
        }

        if (loadingKeys.add(key)) {
            executor.submit(() -> {
                ImageIcon loaded = loadAndScale(pathOrUrl.trim(), size);
                if (loaded != null) {
                    cache.put(key, loaded);
                } else {
                    cache.put(key, size == DEFAULT_SIZE ? placeholder24 : createPlaceholder(size));
                }
                loadingKeys.remove(key);
                CopyOnWriteArrayList<Runnable> callbacks = pendingCallbacks.remove(key);
                if (callbacks != null) {
                    SwingUtilities.invokeLater(() -> {
                        for (Runnable cb : callbacks) {
                            cb.run();
                        }
                    });
                }
            });
        }

        return size == DEFAULT_SIZE ? placeholder24 : createPlaceholder(size);
    }

    /**
     * Synchronously reads an image from HTTP(S), classpath, or local disk and scales it to {@code size x size}.
     *
     * @param source image path or URL
     * @param size   target square dimension in pixels
     * @return scaled ImageIcon, or null if loading fails
     */
    private ImageIcon loadAndScale(String source, int size) {
        try {
            BufferedImage img = null;
            if (source.startsWith("http://") || source.startsWith("https://")) {
                URL url = URI.create(source).toURL();
                URLConnection conn = url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                try (InputStream is = conn.getInputStream()) {
                    img = ImageIO.read(is);
                }
            } else if (source.startsWith("classpath:")) {
                String resPath = source.substring("classpath:".length());
                try (InputStream is = IconCacheService.class.getResourceAsStream(resPath)) {
                    if (is != null) {
                        img = ImageIO.read(is);
                    }
                }
            } else {
                File file = new File(source);
                if (file.isFile()) {
                    img = ImageIO.read(file);
                }
            }
            if (img == null) {
                return null;
            }
            Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            BufferedImage buffered = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = buffered.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(scaled, 0, 0, null);
            g2.dispose();
            return new ImageIcon(buffered);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Generates a simple rounded-square placeholder icon for missing or loading images.
     *
     * @param size width and height in pixels
     * @return generated placeholder ImageIcon
     */
    private static ImageIcon createPlaceholder(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(225, 230, 238));
        g2.fillRoundRect(2, 2, size - 4, size - 4, 6, 6);
        g2.setColor(new Color(145, 155, 170));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(2, 2, size - 4, size - 4, 6, 6);
        int m = size / 2;
        g2.drawLine(m - 3, m, m + 3, m);
        g2.drawLine(m, m - 3, m, m + 3);
        g2.dispose();
        return new ImageIcon(img);
    }

    /**
     * Shuts down the background icon-loading thread pool.
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
