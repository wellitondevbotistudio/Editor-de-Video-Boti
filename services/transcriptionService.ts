/**
 * Transcription Service Abstraction
 * This layer is prepared to receive real Speech-to-Text providers like
 * Whisper (Local/Cloud), Google STT, or OpenAI API.
 */

export interface TranscriptionSegment {
  id: string;
  text: string;
  startTime: number; // in milliseconds
  endTime: number;   // in milliseconds
}

export interface TranscriptionResult {
  segments: TranscriptionSegment[];
  language?: string;
}

export async function transcribeMedia(
  uri: string,
  languageHint?: string
): Promise<TranscriptionResult> {
  // TODO: Implement real STT integration here.
  // For now, this returns an empty result to avoid fake mocks.
  // The system is ready to receive segments from any provider.

  console.log('Transcription requested for:', uri);

  // Return empty result to be filled by future real implementation
  return {
    segments: [],
    language: languageHint || 'auto'
  };
}
