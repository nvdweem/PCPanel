package com.getpcpanel.commands;

import java.util.Optional;

import com.getpcpanel.commands.command.Command;

import javafx.scene.image.Image;

public interface IIconHandler<C extends Command> {
    Class<C> getCommandClass();

    Optional<Image> supplyImage(C cmd);
}
