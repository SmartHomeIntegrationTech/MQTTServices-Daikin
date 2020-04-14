package de.karstenbecker.daikin.ui;

import java.awt.event.ActionEvent;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

class BeanPropertyModel<T> extends AbstractTableModel {

	public List<T> data;
	private PropertyDescriptor[] descriptors;
	private Method[] readMethods, writeMethods;

	private boolean dirty = false;
	
	public Runnable ifDirtyCallback;

	public BeanPropertyModel(Class<T> clazz, Collection<T> data, String... order) {
		this.data = new ArrayList<>(data);
		try {
			BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
			descriptors = beanInfo.getPropertyDescriptors();
			Map<String, PropertyDescriptor> props = new HashMap<String, PropertyDescriptor>();
			for (PropertyDescriptor propertyDescriptor : descriptors) {
				props.put(propertyDescriptor.getDisplayName(), propertyDescriptor);
			}
			descriptors = new PropertyDescriptor[order.length];
			readMethods = new Method[order.length];
			writeMethods = new Method[order.length];
			for (int i = 0; i < order.length; i++) {
				String string = order[i];
				PropertyDescriptor descriptor = props.get(string);
				descriptors[i] = descriptor;
				readMethods[i] = descriptor.getReadMethod();
				writeMethods[i] = descriptor.getWriteMethod();
			}
		} catch (IntrospectionException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -97883351046711039L;

	@Override
	public int getRowCount() {
		return data.size();
	}

	@Override
	public int getColumnCount() {
		return descriptors.length;
	}

	@Override
	public String getColumnName(int column) {
		return descriptors[getIndex(column)].getDisplayName();
	}

	private int getIndex(int column) {
		return column;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return descriptors[getIndex(columnIndex)].getWriteMethod() != null;
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		Method method = writeMethods[getIndex(columnIndex)];
		try {
			T property = data.get(rowIndex);
			method.invoke(property, aValue);
			setDirty(true);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return descriptors[getIndex(columnIndex)].getPropertyType();
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		T property = data.get(rowIndex);
		try {
			return readMethods[getIndex(columnIndex)].invoke(property);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void updateColumns(JTable table) {
		for (int i = 0; i < descriptors.length; i++) {
			PropertyDescriptor prop = descriptors[i];
			if (prop.getPropertyType().isEnum()) {
				Object[] values = prop.getPropertyType().getEnumConstants();
				JComboBox<Object> combo = new JComboBox<>();
				for (Object object : values) {
					combo.addItem(object);
				}
				DefaultCellEditor editor = new DefaultCellEditor(combo);
				table.getColumnModel().getColumn(i).setCellEditor(editor);
			}
			if (prop.getPropertyType().equals(Boolean.class)) {
				table.getColumnModel().getColumn(i).setMaxWidth(64);
			}
		}
	}

	public boolean isDirty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		this.dirty = dirty;
		if (ifDirtyCallback!=null) {
			ifDirtyCallback.run();
		}
	}

}