package com.getpcpanel.commands;

import java.util.Optional;

import com.getpcpanel.commands.command.Command;

import java.awt.image.BufferedImage;

public interface IIconHandler<C extends Command> {
    Class<C> getCommandClass();

    Optional<BufferedImage> supplyImage(C cmd);
}
