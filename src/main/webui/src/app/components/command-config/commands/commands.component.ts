import { ChangeDetectionStrategy, Component, computed, effect, inject, input, Type, viewChildren } from '@angular/core';
import { Command, Commands, CommandType } from '../../../models/generated/backend.types';
import { MatButton } from '@angular/material/button';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { CommandsService } from './commands-service';
import { commandComponentMap, validateCommands } from './commands.components';
import { FieldTree } from '@angular/forms/signals';
import { MatAccordion, MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle } from '@angular/material/expansion';
import { NgComponentOutlet } from '@angular/common';

@Component({
  selector: 'app-commands',
  imports: [
    MatButton,
    MatMenuTrigger,
    MatMenu,
    MatMenuItem,
    MatExpansionPanel,
    MatExpansionPanelHeader,
    MatAccordion,
    NgComponentOutlet,
    MatExpansionPanelTitle
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './commands.component.html',
  styleUrl: './commands.component.scss',
})
export class CommandsComponent {
  private readonly commandService = inject(CommandsService);
  commands = input.required<FieldTree<Commands>>();
  private accordion = viewChildren(MatExpansionPanel);

  private keepAccordionOpen = effect(this.initialFirstThenLastEffect());

  type = input.required<'dial' | 'button'>();

  protected availableCommands = computed(() => (this.commandService.commands.value() ?? []).filter(c => c.kind === this.type()));
  private validateCommands = effect(() => validateCommands(this.availableCommands()));

  protected nameOf(_type: string) {
    return this.availableCommands().find(c => c.command === _type)?.name ?? 'unknown';
  }

  protected componentFor(cmd: Command): Type<unknown> | undefined {
    return commandComponentMap.get(cmd._type)?.component;
  }

  protected addCommand(command: CommandType) {
    const def = commandComponentMap.get(command.command);
    if (!def) {
      console.error(`No component found for command type ${command.command}`);
      return;
    }

    this.commands().commands().value.update(commands => ([...commands, def.buildEmpty() as Command]));
  }

  private initialFirstThenLastEffect() {
    let first = true;
    return () => {
      const idx = first ? 0 : -1;
      this.accordion().at(idx)?.open();
      first = false;
    };
  }
}
