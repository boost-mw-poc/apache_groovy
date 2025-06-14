/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package groovy.swing.table;

import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Date;
import java.util.Vector;

/**
 * A sorter for TableModels.
 * <p>
 * The sorter has a model (conforming to TableModel)
 * and itself implements TableModel. TableSorter does not store or copy
 * the data in the TableModel, instead it maintains an array of
 * integers which it keeps the same size as the number of rows in its
 * model. When the model changes it notifies the sorter that something
 * has changed eg. "rowsAdded" so that its internal array of integers
 * can be reallocated. As requests are made of the sorter (like
 * getValueAt(row, col) it redirects them to its model via the mapping
 * array. That way the TableSorter appears to hold another copy of the table
 * with the rows in a different order. The sorting algorithm used is stable
 * which means that it does not move around rows when its comparison
 * function returns 0 to denote that they are equivalent.
 */
public class TableSorter extends TableMap {
    private static final int[] EMPTY_INT_ARRAY = new int[0];
    int[] indexes;
    Vector<Integer> sortingColumns = new Vector<>();
    boolean ascending = true;
    int lastSortedColumn = -1;

    public TableSorter() {
        indexes = EMPTY_INT_ARRAY; // For consistency.
    }

    public TableSorter(TableModel model) {
        setModel(model);
    }

    @Override
    public void setModel(TableModel model) {
        super.setModel(model);
        reallocateIndexes();
    }

    public int compareRowsByColumn(int row1, int row2, int column) {
        Class<?> type = model.getColumnClass(column);
        TableModel data = model;

        // Check for nulls

        Object o1 = data.getValueAt(row1, column);
        Object o2 = data.getValueAt(row2, column);

        // If both values are null return 0
        if (o1 == null && o2 == null) {
            return 0;
        } else if (o1 == null) { // Define null less than everything.
            return -1;
        } else if (o2 == null) {
            return 1;
        }

        /* We copy all returned values from the getValue call in case
        an optimised model is reusing one object to return many values.
        The Number subclasses in the JDK are immutable and so will not be used in
        this way but other subclasses of Number might want to do this to save
        space and avoid unnecessary heap allocation.
        */
        if (type.getSuperclass() == java.lang.Number.class) {
            return compareNumbers(data, row1, column, row2);
        }

        if (type == java.util.Date.class) {
            return compareDates(data, row1, column, row2);
        }

        if (type == String.class) {
            return compareStrings(data, row1, column, row2);
        }

        if (type == Boolean.class) {
            return compareBooleans(data, row1, column, row2);
        }
        return compareObjects(data, row1, column, row2);
    }

    private static int compareObjects(TableModel data, int row1, int column, int row2) {
        Object v1 = data.getValueAt(row1, column);
        String s1 = v1.toString();
        Object v2 = data.getValueAt(row2, column);
        String s2 = v2.toString();
        int result = s1.compareTo(s2);

        return Integer.compare(result, 0);
    }

    private static int compareBooleans(TableModel data, int row1, int column, int row2) {
        boolean b1 = (Boolean) data.getValueAt(row1, column);
        boolean b2 = (Boolean) data.getValueAt(row2, column);

        if (b1 == b2)
            return 0;
        if (b1) // Define false < true
            return 1;
        return -1;
    }

    private static int compareStrings(TableModel data, int row1, int column, int row2) {
        String s1 = (String) data.getValueAt(row1, column);
        String s2 = (String) data.getValueAt(row2, column);
        int result = s1.compareTo(s2);

        return Integer.compare(result, 0);
    }

    private static int compareDates(TableModel data, int row1, int column, int row2) {
        Date d1 = (Date) data.getValueAt(row1, column);
        long n1 = d1.getTime();
        Date d2 = (Date) data.getValueAt(row2, column);
        long n2 = d2.getTime();

        return Long.compare(n1, n2);
    }

    private static int compareNumbers(TableModel data, int row1, int column, int row2) {
        Number n1 = (Number) data.getValueAt(row1, column);
        double d1 = n1.doubleValue();
        Number n2 = (Number) data.getValueAt(row2, column);
        double d2 = n2.doubleValue();

        return Double.compare(d1, d2);
    }

    public int compare(int row1, int row2) {
        for (int level = 0; level < sortingColumns.size(); level++) {
            Integer column = (Integer) sortingColumns.elementAt(level);
            int result = compareRowsByColumn(row1, row2, column);
            if (result != 0)
                return ascending ? result : -result;
        }
        return 0;
    }

    public void reallocateIndexes() {
        int rowCount = model.getRowCount();

        // Set up a new array of indexes with the right number of elements
        // for the new data model.
        indexes = new int[rowCount];

        // Initialise with the identity mapping.
        for (int row = 0; row < rowCount; row++)
            indexes[row] = row;
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        reallocateIndexes();

        super.tableChanged(e);
    }

    public void checkModel() {
        if (indexes.length != model.getRowCount()) {
            System.err.println("Sorter not informed of a change in model.");
        }
    }

    public void sort(Object sender) {
        checkModel();
        shuttlesort((int[]) indexes.clone(), indexes, 0, indexes.length);
    }

    public void n2sort() {
        for (int i = 0; i < getRowCount(); i++) {
            for (int j = i + 1; j < getRowCount(); j++) {
                if (compare(indexes[i], indexes[j]) == -1) {
                    swap(i, j);
                }
            }
        }
    }

    // This is a home-grown implementation which we have not had time
    // to research - it may perform poorly in some circumstances. It
    // requires twice the space of an in-place algorithm and makes
    // NlogN assignments shuttling the values between the two
    // arrays. The number of compares appears to vary between N-1 and
    // NlogN depending on the initial order but the main reason for
    // using it here is that, unlike qsort, it is stable.
    public void shuttlesort(int[] from, int[] to, int low, int high) {
        if (high - low < 2) {
            return;
        }
        int middle = (low + high) >>> 1;
        shuttlesort(to, from, low, middle);
        shuttlesort(to, from, middle, high);

        int p = low;
        int q = middle;

        /* This is an optional short-cut; at each recursive call,
        check to see if the elements in this subset are already
        ordered.  If so, no further comparisons are needed; the
        sub-array can just be copied.  The array must be copied rather
        than assigned otherwise sister calls in the recursion might
        get out of sync.  When the number of elements is three they
        are partitioned so that the first set, [low, mid), has one
        element and the second, [mid, high), has two. We skip the
        optimisation when the number of elements is three or less as
        the first compare in the normal merge will produce the same
        sequence of steps. This optimisation seems to be worthwhile
        for partially ordered lists but some analysis is needed to
        find out how the performance drops to Nlog(N) as the initial
        order diminishes - it may drop very quickly.  */

        if (high - low >= 4 && compare(from[middle - 1], from[middle]) <= 0) {
            System.arraycopy(from, low, to, low, high - low);
            return;
        }

        // A normal merge.

        for (int i = low; i < high; i++) {
            if (q >= high || (p < middle && compare(from[p], from[q]) <= 0)) {
                to[i] = from[p++];
            } else {
                to[i] = from[q++];
            }
        }
    }

    public void swap(int i, int j) {
        int tmp = indexes[i];
        indexes[i] = indexes[j];
        indexes[j] = tmp;
    }

    // The mapping only affects the contents of the data rows.
    // Pass all requests to these rows through the mapping array: "indexes".

    @Override
    public Object getValueAt(int aRow, int aColumn) {
        checkModel();
        return model.getValueAt(indexes[aRow], aColumn);
    }

    @Override
    public void setValueAt(Object aValue, int aRow, int aColumn) {
        checkModel();
        model.setValueAt(aValue, indexes[aRow], aColumn);
    }

    public void sortByColumn(int column) {
        sortByColumn(column, true);
    }

    public void sortByColumn(int column, boolean ascending) {
        this.ascending = ascending;
        sortingColumns.removeAllElements();
        sortingColumns.addElement(column);
        sort(this);
        super.tableChanged(new TableModelEvent(this));
    }

    // There is nowhere else to put this.
    // Add a mouse listener to the Table to trigger a table sort
    // when a column heading is clicked in the JTable.
    public void addMouseListenerToHeaderInTable(JTable table) {
        final TableSorter sorter = this;
        final JTable tableView = table;
        tableView.setColumnSelectionAllowed(false);
        MouseAdapter listMouseListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TableColumnModel columnModel = tableView.getColumnModel();
                int viewColumn = columnModel.getColumnIndexAtX(e.getX());
                int column = tableView.convertColumnIndexToModel(viewColumn);
                if (e.getClickCount() == 1 && column != -1) {
                    if (lastSortedColumn == column) ascending = !ascending;
                    sorter.sortByColumn(column, ascending);
                    lastSortedColumn = column;
                }
            }
        };
        JTableHeader th = tableView.getTableHeader();
        th.addMouseListener(listMouseListener);
    }

}
