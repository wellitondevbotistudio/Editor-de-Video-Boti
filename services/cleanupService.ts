import * as FileSystem from 'expo-file-system';
import { loadAllProjectsLocal } from './projectService';

/**
 * Identifies internal files that are not referenced by any project.
 */
export async function identifyOrphanFiles(): Promise<string[]> {
  try {
    const projects = await loadAllProjectsLocal();
    const referencedUris = new Set<string>();

    for (const p of projects) {
      if (p.config) {
        try {
          const config = JSON.parse(p.config);
          (config.clips || []).forEach((c: any) => referencedUris.add(c.uri));
          (config.audios || []).forEach((a: any) => referencedUris.add(a.uri));
        } catch (e) {}
      }
      if (p.thumb) referencedUris.add(p.thumb);
    }

    const internalFiles = await FileSystem.readDirectoryAsync(FileSystem.documentDirectory!);
    const orphans: string[] = [];

    for (const filename of internalFiles) {
      const fullUri = `${FileSystem.documentDirectory}${filename}`;
      // Ignore hidden files and directories
      if (filename.startsWith('.')) continue;

      if (!referencedUris.has(fullUri)) {
        orphans.push(fullUri);
      }
    }

    return orphans;
  } catch (e) {
    console.error('Failed to identify orphans:', e);
    return [];
  }
}

/**
 * Deletes files identified as orphans.
 */
export async function cleanupOrphans(): Promise<number> {
  const orphans = await identifyOrphanFiles();
  let count = 0;
  for (const uri of orphans) {
    try {
      await FileSystem.deleteAsync(uri, { idempotent: true });
      count++;
    } catch (e) {
      console.warn(`Failed to delete orphan: ${uri}`);
    }
  }
  return count;
}
