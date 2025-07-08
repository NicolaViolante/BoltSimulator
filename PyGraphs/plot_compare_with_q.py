import os
import sys
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.ticker import MultipleLocator

def dynamic_locator(data_series, n_bins=10, min_step=1.0):
    """Ritorna un MultipleLocator con passo calcolato su data_series"""
    vmin, vmax = data_series.min(), data_series.max()
    step = max((vmax - vmin) / n_bins, min_step)
    return MultipleLocator(step)

def plot_response_times(csv_path, out_dir, time_col='Time', response_col='ETs'):
    df = pd.read_csv(csv_path)
    seed_col = df.columns[0]
    df = df.sort_values(by=time_col)

    fig, ax = plt.subplots(figsize=(10, 6))
    for seed_value, group in df.groupby(seed_col):
        ax.plot(group[time_col], group[response_col], label=str(seed_value), linestyle='-')

    ax.set_title("Andamento dei Tempi di Risposta nel Tempo")
    ax.set_xlabel(time_col)
    ax.set_ylabel(response_col)
    ax.grid(True)

    ax.xaxis.set_major_locator(dynamic_locator(df[time_col], n_bins=10, min_step=1.0))
    ax.yaxis.set_major_locator(dynamic_locator(df[response_col], n_bins=10, min_step=0.1))

    ax.legend(title=seed_col, loc='upper right', bbox_to_anchor=(1, 1))
    fig.tight_layout()

    out_path = os.path.join(out_dir, 'response_times.png')
    fig.savefig(out_path)
    plt.close(fig)
    print(f"– saved: {out_path}")

def plot_queue_times(csv_path, out_dir, time_col='Time', queue_col='ETq'):
    df = pd.read_csv(csv_path)
    seed_col = df.columns[0]
    df = df.sort_values(by=time_col)

    fig, ax = plt.subplots(figsize=(10, 6))
    for seed_value, group in df.groupby(seed_col):
        ax.plot(group[time_col], group[queue_col], label=str(seed_value), linestyle='-')

    ax.set_title("Andamento dei Tempi di Coda nel Tempo")
    ax.set_xlabel(time_col)
    ax.set_ylabel(queue_col)
    ax.grid(True)

    ax.xaxis.set_major_locator(dynamic_locator(df[time_col], n_bins=10, min_step=1.0))
    ax.yaxis.set_major_locator(dynamic_locator(df[queue_col], n_bins=10, min_step=0.1))

    ax.legend(title=seed_col, loc='upper right', bbox_to_anchor=(1, 1))
    fig.tight_layout()

    out_path = os.path.join(out_dir, 'queue_times.png')
    fig.savefig(out_path)
    plt.close(fig)
    print(f"– saved: {out_path}")

def plot_job_counts(csv_path, out_dir, time_col='Time', jobs_col='ENs'):
    df = pd.read_csv(csv_path)
    seed_col = df.columns[0]
    df = df.sort_values(by=time_col)

    fig, ax = plt.subplots(figsize=(10, 6))
    for seed_value, group in df.groupby(seed_col):
        ax.plot(group[time_col], group[jobs_col], label=str(seed_value), linestyle='-')

    ax.set_title("Andamento del Numero di Job nel Centro nel Tempo")
    ax.set_xlabel(time_col)
    ax.set_ylabel(jobs_col)
    ax.grid(True)

    ax.xaxis.set_major_locator(dynamic_locator(df[time_col], n_bins=10, min_step=1.0))
    ax.yaxis.set_major_locator(dynamic_locator(df[jobs_col], n_bins=10, min_step=1.0))

    ax.legend(title=seed_col, loc='upper right', bbox_to_anchor=(1, 1))
    fig.tight_layout()

    out_path = os.path.join(out_dir, 'job_counts.png')
    fig.savefig(out_path)
    plt.close(fig)
    print(f"– saved: {out_path}")

def plot_infinite_response(csv_path, out_dir, batch_col='Batch', response_col='ETs'):
    df = pd.read_csv(csv_path)
    df = df.sort_values(by=batch_col)

    fig, ax = plt.subplots(figsize=(10, 6))
    ax.plot(df[batch_col], df[response_col], marker='o', linestyle='-')

    ax.set_title("Andamento ETs Cumulativo per Batch")
    ax.set_xlabel(batch_col)
    ax.set_ylabel(response_col)
    ax.grid(True)

    ax.xaxis.set_major_locator(dynamic_locator(df[batch_col], n_bins=10, min_step=1))
    ax.yaxis.set_major_locator(dynamic_locator(df[response_col], n_bins=10, min_step=0.1))

    fig.tight_layout()
    out_path = os.path.join(out_dir, 'infinite_response_times.png')
    fig.savefig(out_path)
    plt.close(fig)
    print(f"– saved: {out_path}")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python plot_compare_with_q.py <csv_path> [<out_dir>] [<infinite_csv>]")
        sys.exit(1)

    csv_path = sys.argv[1]
    out_dir  = sys.argv[2] if len(sys.argv) > 2 else os.getcwd()
    os.makedirs(out_dir, exist_ok=True)

    # Lettura header per controllare colonne
    df_head = pd.read_csv(csv_path, nrows=0)
    cols = list(df_head.columns)
    print(f"– detected columns: {cols}")

    # Verifica presenza colonne
    is_time_based = 'Time' in cols and 'ETs' in cols
    has_queue = 'ETq' in cols
    is_batch_based = 'Batch' in cols and 'ETs' in cols

    if is_time_based:
        plot_response_times(csv_path, out_dir)
        plot_queue_times(csv_path, out_dir)
        plot_job_counts(csv_path, out_dir)
        if len(sys.argv) > 3:
            plot_infinite_response(sys.argv[3], out_dir)
    elif is_batch_based:
        plot_infinite_response(csv_path, out_dir)
    else:
        print(f"Errore: le colonne di {csv_path} non corrispondono: {cols}")
        sys.exit(1)

    print(f"\nTutti i plot sono stati salvati in: {out_dir}")
