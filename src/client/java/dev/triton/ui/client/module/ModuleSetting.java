package dev.triton.ui.client.module;

public abstract class ModuleSetting<T> {
	private final String id;
	private final String label;
	private final String description;
	private T value;

	protected ModuleSetting(String id, String label, String description, T value) {
		this.id = id;
		this.label = label;
		this.description = description;
		this.value = value;
	}

	public String id() {
		return id;
	}

	public String label() {
		return label;
	}

	public String description() {
		return description;
	}

	public T value() {
		return value;
	}

	public void setValue(T value) {
		this.value = value;
	}
}
