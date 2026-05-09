import { Command, Commands, DialAction } from '../../models/generated/backend.types';
import { buildEmptyDialCommandParams } from '../../components/command-config/commands/commands.components';


function ensureDialDataSingle(cmd: Command): Command {
  const dialData = cmd as unknown as DialAction;
  dialData.dialParams = {
    ...buildEmptyDialCommandParams(),
    ...(dialData.dialParams ?? {})
  };
  return cmd;
}

export function ensureDialData(ensureCommands: Commands | undefined): Commands | undefined {
  return ensureCommands ? {
    ...ensureCommands,
    commands: ensureCommands.commands.map(ensureDialDataSingle),
  } : undefined;
}
