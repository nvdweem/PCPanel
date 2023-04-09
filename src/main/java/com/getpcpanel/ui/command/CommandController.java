package com.getpcpanel.ui.command;

import java.util.Arrays;

import com.getpcpanel.commands.command.Command;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public abstract class CommandController<T extends Command> {
    private final BooleanProperty initialized = new SimpleBooleanProperty(false);

    public abstract void postInit(CommandContext context);

    public void initFromCommand(T cmd) {
        initialized.set(true);
    }

    public StringProperty additionalLabelText() {
        var old = determineDependencies();
        var dependencies = Arrays.copyOf(old, old.length + 1);
        dependencies[old.length] = initialized;

        var result = new SimpleStringProperty();
        result.bind(Bindings.createStringBinding(() -> buildLabelCommand().buildLabel(), dependencies));
        return result;
    }

    protected abstract Observable[] determineDependencies();

    protected abstract Command buildLabelCommand();
}
