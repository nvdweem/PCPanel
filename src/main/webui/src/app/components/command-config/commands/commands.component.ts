import { ChangeDetectionStrategy, Component, computed, effect, inject, input, Type, viewChildren } from '@angular/core';
import { Command, Commands, CommandType, DialCommandParams } from '../../../models/generated/backend.types';
import { MatIconButton, MatMiniFabButton } from '@angular/material/button';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { CommandsService } from './commands-service';
import { commandComponentMap, validateCommands } from './commands.components';
import { FieldTree, FormField } from '@angular/forms/signals';
import { MatAccordion, MatExpansionPanel, MatExpansionPanelDescription, MatExpansionPanelHeader, MatExpansionPanelTitle } from '@angular/material/expansion';
import { NgComponentOutlet, NgTemplateOutlet, TitleCasePipe } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { DialParamsEditorComponent, DialParamsEditorComponentData } from '../dial-params-editor.component';

const grpToIdx = new Map([['standard', 0], ['obs', 1], ['wavelink', 2], ['voicemeeter', 3]]);

function commandGroupIndexOf(grp: string): number {
  return grpToIdx.get(grp) ?? 100;
}

@Component({
  selector: 'app-commands',
  imports: [
    MatMenuTrigger,
    MatMenu,
    MatExpansionPanel,
    MatExpansionPanelHeader,
    MatAccordion,
    NgComponentOutlet,
    MatExpansionPanelTitle,
    MatExpansionPanelDescription,
    MatIconButton,
    MatIcon,
    MatMenuItem,
    TitleCasePipe,
    MatMiniFabButton,
    NgTemplateOutlet,
    MatCheckbox,
    FormField
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './commands.component.html',
  styleUrl: './commands.component.scss',
})
export class CommandsComponent {
  private readonly commandService = inject(CommandsService);
  private readonly dialog = inject(MatDialog);
  commands = input.required<FieldTree<Commands>>();
  type = input.required<'dial' | 'button'>();

  private readonly accordion = viewChildren(MatExpansionPanel);
  private keepAccordionOpen = effect(this.initialFirstThenLastEffect());

  private allCommands = computed(() => (this.commandService.commands.value() ?? []).filter(c => c.kind === this.type()));
  protected commandGroups = computed(() => Object.keys(this.groupedCommands()).sort(commandGroupIndexOf));
  protected groupedCommands = computed(() => Object.groupBy(this.allCommands().toSorted((l, r) => l.name.localeCompare(r.name)), c => c.category));
  private validateCommands = effect(() => validateCommands(this.allCommands()));

  protected nameOf(_type: string) {
    return this.allCommands().find(c => c.command === _type)?.name ?? 'unknown';
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

  protected delete(index: number, event?: MouseEvent) {
    event?.stopPropagation();
    this.commands().commands().value.update(val => val.filter((_, i) => i !== index));
  }

  protected editDialParams(cmd: FieldTree<Required<DialCommandParams>>) {
    this.dialog.open<DialParamsEditorComponent, DialParamsEditorComponentData>(DialParamsEditorComponent, {
      data: {command: cmd},
    });
  }
}
