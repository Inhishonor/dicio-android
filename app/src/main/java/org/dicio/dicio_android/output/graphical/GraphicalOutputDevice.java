package org.dicio.dicio_android.output.graphical;

import android.view.View;

import androidx.annotation.NonNull;

/**
 * An interface that has to be implemented by classes that wish to
 * display the output generated by components.<br>
 * TODO Some more methods could be added in the future, for example
 * `clearScreen()`, `addDivider()` or something along those lines.
 */
public interface GraphicalOutputDevice {
    /**
     * Displays graphical output to the user
     * @param graphicalOutput a view to show (usually encapsulates inside an output container)
     * @param addDivider whether to add a divider right after the added view or not. Useful when
     *                   displaying multiple separated yet related things at once.
     */
    void display(@NonNull View graphicalOutput, boolean addDivider);
}