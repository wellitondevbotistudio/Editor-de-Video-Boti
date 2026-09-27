import * as SQLite from 'expo-sqlite';

export interface ProjectData {
  id: string;
  title: string;
  duration: string;
  date: string;
  thumb: string;
  config?: string; // Stringified JSON holding clips, duration, texts, audios, effects, etc.
}

let dbInstance: any = null;

async function getDB() {
  if (!dbInstance) {
    dbInstance = await SQLite.openDatabaseAsync('videoeditor.db');
    // Ensure table exists
    await dbInstance.execAsync(`
      CREATE TABLE IF NOT EXISTS projects (
        id TEXT PRIMARY KEY NOT NULL,
        title TEXT NOT NULL,
        duration TEXT NOT NULL,
        date TEXT NOT NULL,
        thumb TEXT NOT NULL,
        config TEXT
      );
    `);
  }
  return dbInstance;
}

export async function loadAllProjectsLocal(): Promise<ProjectData[]> {
  try {
    const db = await getDB();
    const rows = await db.getAllAsync('SELECT * FROM projects ORDER BY date DESC, id DESC');
    return rows as ProjectData[];
  } catch (error) {
    console.error('Error loading local projects:', error);
    return [];
  }
}

export async function saveProjectLocal(project: ProjectData): Promise<void> {
  try {
    const db = await getDB();
    await db.runAsync(
      `INSERT INTO projects (id, title, duration, date, thumb, config)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         title = excluded.title,
         duration = excluded.duration,
         date = excluded.date,
         thumb = excluded.thumb,
         config = excluded.config`,
      [project.id, project.title, project.duration, project.date, project.thumb, project.config || '']
    );
  } catch (error) {
    console.error('Error saving local project:', error);
  }
}

export async function deleteProjectLocal(id: string): Promise<void> {
  try {
    const db = await getDB();
    await db.runAsync('DELETE FROM projects WHERE id = ?', [id]);
  } catch (error) {
    console.error('Error deleting local project:', error);
  }
}
