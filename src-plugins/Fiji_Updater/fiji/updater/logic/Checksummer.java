package fiji.updater.logic;

import fiji.updater.logic.PluginObject.Status;

import fiji.updater.util.Progress;
import fiji.updater.util.Progressable;
import fiji.updater.util.Util;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Checksummer's overall role is to be in charge of building of a plugin
 * list for interface usage.
 *
 * 1st step: Get information of local plugins (checksums and version)
 * 2nd step: Given XML file, get information of latest Fiji plugins (checksums
 * and version)
 * 3rd step: Build up list of "PluginObject" using both local and updates
 *
 * digests and dates hold checksums and versions of local plugins respectively
 * latestDigests and latestDates hold checksums and versions of latest Fiji
 * plugins
 */
public class Checksummer extends Progressable {
	int counter, total;

	public Checksummer(Progress progress) {
		addProgress(progress);
		setTitle("Checksumming");
	}

	static class StringPair {
		String path, realPath;
		StringPair(String path, String realPath) {
			this.path = path;
			this.realPath = realPath;
		}
	}

	protected List<StringPair> queue;

	public void queueDir(String[] dirs, String[] extensions) {
		Set<String> set = new HashSet<String>();
		for (String extension : extensions)
			set.add(extension);
		for (String dir : dirs)
			queueDir(dir, set);
	}

	public void queueDir(String dir, Set<String> extensions) {
		File file = new File(Util.prefix(dir));
		if (!file.exists())
			return;
		for (String item : file.list()) {
			String path = dir + "/" + item;
			file = new File(Util.prefix(path));
			if (file.isDirectory()) {
				if (!item.equals(".") && !item.equals(".."))
					queueDir(path, extensions);
				continue;
			}
			int dot = item.lastIndexOf('.');
			if (dot < 0 || !extensions.contains(item.substring(dot)))
				continue;
			queue(path);
		}
	}

	protected void queueIfExists(String path) {
		if (new File(Util.prefix(path)).exists())
			queue(path);
	}

	protected void queue(String path) {
		queue(path, path);
	}

	protected void queue(String path, String realPath) {
		queue.add(new StringPair(path, realPath));
	}

	protected void handle(StringPair pair) {
		String path = pair.path;
		String realPath = Util.prefix(pair.realPath);
		addItem(path);

		String checksum = null;
		long timestamp = 0;
		if (new File(path).exists()) try {
			checksum = Util.getDigest(path, realPath);
			timestamp = Util.getTimestamp(realPath);
		} catch (Exception e) { e.printStackTrace(); }

		PluginCollection plugins = PluginCollection.getInstance();
		PluginObject plugin = plugins.getPlugin(path);
		if (plugin == null)
			plugins.add(new PluginObject(path, checksum,
				timestamp, Status.NOT_FIJI));
		else if (checksum != null) {
			plugin.setLocalVersion(checksum, timestamp);
			counter += (int)Util.getFilesize(realPath);
		}
		setItemCount(1, 1);
		setCount(counter, total);
	}

	protected void handleQueue() {
		total = 0;
		for (StringPair pair : queue)
			total += Util.getFilesize(pair.realPath);
		counter = 0;
		for (StringPair pair : queue)
			handle(pair);
		done();
	}

	public void updateFromLocal(List<String> files) {
		if (!Util.isDeveloper)
			throw new RuntimeException("Must be developer");
		queue = new ArrayList<StringPair>();
		for (String file : files)
			queue(file);
		handleQueue();
	}

	public void updateFromLocal() {
		queue = new ArrayList<StringPair>();

		for (String launcher : Util.isDeveloper ?
					Util.launchers : Util.getLaunchers())
				queueIfExists(launcher);

		queue("ij.jar");

		queueDir(new String[] { "jars", "retro", "misc" },
				new String[] { ".jar", ".class" });
		queueDir(new String[] { "plugins" },
				new String[] { ".jar", ".class",
					".py", ".rb", ".clj", ".js", ".bsh",
					".txt", ".ijm" });
		queueDir(new String[] { "macros" },
				new String[] { ".txt", ".ijm" });
		queueDir(new String[] { "luts" }, new String[] { ".lut" });

		handleQueue();
	}
}
