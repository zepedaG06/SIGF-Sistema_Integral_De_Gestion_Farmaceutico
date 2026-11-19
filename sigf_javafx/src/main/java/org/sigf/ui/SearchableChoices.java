package org.sigf.ui;

import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import java.util.Collection;
import java.util.Locale;

final class SearchableChoices {
 private SearchableChoices() {}
 static ComboBox<String> create(Collection<String> options, String initial) {
  var all=FXCollections.observableArrayList(options);
  FilteredList<String> filtered=new FilteredList<>(all, item->true);
  ComboBox<String> combo=new ComboBox<>(filtered);combo.setEditable(true);combo.setMaxWidth(Double.MAX_VALUE);combo.setValue(initial);
  combo.getEditor().setOnKeyReleased(e->{KeyCode key=e.getCode();if(key==KeyCode.UP||key==KeyCode.DOWN||key==KeyCode.ENTER||key==KeyCode.ESCAPE||key==KeyCode.TAB)return;String q=combo.getEditor().getText().toLowerCase(Locale.ROOT).trim();filtered.setPredicate(item->item.toLowerCase(Locale.ROOT).contains(q));if(!filtered.isEmpty()&&!combo.isShowing())combo.show();});
  combo.setOnHidden(e->filtered.setPredicate(item->true));return combo;
 }
}
