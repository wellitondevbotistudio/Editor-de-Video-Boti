import { FFmpegKit, ReturnCode } from 'ffmpeg-kit-react-native';

export const exportVideo = async (inputPath: string, outputPath: string) => {
  // Comando de exemplo: corta os primeiros 5 segundos
  const command = `-y -i ${inputPath} -t 5 -c:v mpeg4 ${outputPath}`;
  const session = await FFmpegKit.execute(command);
  const returnCode = await session.getReturnCode();

  if (ReturnCode.isSuccess(returnCode)) {
    console.log("Sucesso:", outputPath);
    return outputPath;
  }
  throw new Error("Erro no FFmpeg.");
};