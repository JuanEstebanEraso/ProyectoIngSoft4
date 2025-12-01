#!/usr/bin/env python3
"""
Script para graficar resultados de experimentos de escalabilidad
Genera gráficos de:
1. Tiempo de procesamiento vs Tamaño de datos
2. Speedup vs Número de workers
3. Throughput vs Número de workers
4. Distribución de tiempos por fase
"""

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
import sys

# Configuración de estilo
sns.set_style("whitegrid")
plt.rcParams['figure.figsize'] = (12, 8)
plt.rcParams['font.size'] = 10

def load_data(filepath='experiment_results.csv'):
    """Carga los datos de experimentos desde CSV"""
    if not os.path.exists(filepath):
        print(f"❌ Error: No se encuentra el archivo {filepath}")
        print("Ejecuta primero: java -jar client.jar experiment")
        sys.exit(1)
    
    df = pd.read_csv(filepath)
    print(f"✅ Datos cargados: {len(df)} experimentos")
    print(f"   Tamaños: {df['datagram_count'].unique()}")
    print(f"   Workers: {df['workers'].unique()}")
    return df

def plot_time_vs_size(df, output_dir='experiment_results/plots'):
    """Gráfico 1: Tiempo total vs Tamaño de datos (por número de workers)"""
    os.makedirs(output_dir, exist_ok=True)
    
    plt.figure(figsize=(12, 7))
    
    for workers in sorted(df['workers'].unique()):
        data = df[df['workers'] == workers]
        label = f'{workers} workers' if workers > 0 else 'Local (sin workers)'
        plt.plot(data['datagram_count'], data['total_time_ms'] / 1000, 
                marker='o', linewidth=2, markersize=8, label=label)
    
    plt.xlabel('Tamaño del Dataset (datagramas)', fontsize=12, fontweight='bold')
    plt.ylabel('Tiempo de Procesamiento (segundos)', fontsize=12, fontweight='bold')
    plt.title('Tiempo de Procesamiento vs Tamaño de Datos\n(Análisis de Escalabilidad)', 
              fontsize=14, fontweight='bold', pad=20)
    plt.legend(fontsize=11)
    plt.grid(True, alpha=0.3)
    plt.xscale('log')
    plt.yscale('log')
    
    # Formato de números
    ax = plt.gca()
    ax.xaxis.set_major_formatter(plt.FuncFormatter(lambda x, p: f'{int(x):,}'))
    
    plt.tight_layout()
    filename = os.path.join(output_dir, 'time_vs_size.png')
    plt.savefig(filename, dpi=300, bbox_inches='tight')
    print(f"✅ Gráfico guardado: {filename}")
    plt.close()

def plot_speedup(df, output_dir='experiment_results/plots'):
    """Gráfico 2: Speedup vs Número de workers"""
    os.makedirs(output_dir, exist_ok=True)
    
    plt.figure(figsize=(12, 7))
    
    # Calcular speedup para cada tamaño de datos
    for size in sorted(df['datagram_count'].unique()):
        data = df[df['datagram_count'] == size].sort_values('workers')
        
        if len(data) == 0:
            continue
        
        # Tiempo base (sin workers o con menos workers)
        baseline_time = data['total_time_ms'].iloc[0]
        
        # Calcular speedup
        speedup = baseline_time / data['total_time_ms']
        
        label = f'{size:,} datagramas'
        plt.plot(data['workers'], speedup, marker='o', linewidth=2, 
                markersize=8, label=label)
    
    # Línea ideal (speedup lineal)
    max_workers = df['workers'].max()
    if max_workers > 0:
        plt.plot([0, max_workers], [1, max_workers], 'k--', 
                linewidth=1.5, alpha=0.5, label='Speedup ideal (lineal)')
    
    plt.xlabel('Número de Workers', fontsize=12, fontweight='bold')
    plt.ylabel('Speedup (tiempo_base / tiempo_actual)', fontsize=12, fontweight='bold')
    plt.title('Speedup vs Número de Workers\n(Eficiencia del Procesamiento Distribuido)', 
              fontsize=14, fontweight='bold', pad=20)
    plt.legend(fontsize=11)
    plt.grid(True, alpha=0.3)
    
    plt.tight_layout()
    filename = os.path.join(output_dir, 'speedup.png')
    plt.savefig(filename, dpi=300, bbox_inches='tight')
    print(f"✅ Gráfico guardado: {filename}")
    plt.close()

def plot_throughput(df, output_dir='experiment_results/plots'):
    """Gráfico 3: Throughput vs Número de workers"""
    os.makedirs(output_dir, exist_ok=True)
    
    plt.figure(figsize=(12, 7))
    
    for size in sorted(df['datagram_count'].unique()):
        data = df[df['datagram_count'] == size].sort_values('workers')
        
        if len(data) == 0:
            continue
        
        label = f'{size:,} datagramas'
        plt.plot(data['workers'], data['throughput_dps'] / 1000, 
                marker='o', linewidth=2, markersize=8, label=label)
    
    plt.xlabel('Número de Workers', fontsize=12, fontweight='bold')
    plt.ylabel('Throughput (miles de datagramas/segundo)', fontsize=12, fontweight='bold')
    plt.title('Throughput vs Número de Workers\n(Capacidad de Procesamiento)', 
              fontsize=14, fontweight='bold', pad=20)
    plt.legend(fontsize=11)
    plt.grid(True, alpha=0.3)
    
    plt.tight_layout()
    filename = os.path.join(output_dir, 'throughput.png')
    plt.savefig(filename, dpi=300, bbox_inches='tight')
    print(f"✅ Gráfico guardado: {filename}")
    plt.close()

def plot_time_breakdown(df, output_dir='experiment_results/plots'):
    """Gráfico 4: Distribución de tiempos por fase"""
    os.makedirs(output_dir, exist_ok=True)
    
    # Tomar el último experimento de cada configuración
    latest = df.groupby(['datagram_count', 'workers']).last().reset_index()
    
    fig, axes = plt.subplots(1, len(latest['datagram_count'].unique()), 
                            figsize=(16, 6), sharey=True)
    
    if len(latest['datagram_count'].unique()) == 1:
        axes = [axes]
    
    for idx, size in enumerate(sorted(latest['datagram_count'].unique())):
        ax = axes[idx]
        data = latest[latest['datagram_count'] == size]
        
        phases = ['load_time_ms', 'separation_time_ms', 
                 'distribution_time_ms', 'consolidation_time_ms']
        labels = ['Carga CSV', 'Separación', 'Distribución', 'Consolidación']
        
        x = data['workers'].values
        bottom = [0] * len(x)
        
        colors = ['#3498db', '#e74c3c', '#2ecc71', '#f39c12']
        
        for phase, label, color in zip(phases, labels, colors):
            values = data[phase].values
            ax.bar(x, values, bottom=bottom, label=label, color=color, alpha=0.8)
            bottom = [b + v for b, v in zip(bottom, values)]
        
        ax.set_xlabel('Workers', fontsize=11, fontweight='bold')
        ax.set_title(f'{size:,} datagramas', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')
    
    axes[0].set_ylabel('Tiempo (ms)', fontsize=11, fontweight='bold')
    axes[-1].legend(fontsize=10, loc='upper right')
    
    fig.suptitle('Distribución de Tiempos por Fase de Procesamiento', 
                fontsize=14, fontweight='bold', y=1.02)
    
    plt.tight_layout()
    filename = os.path.join(output_dir, 'time_breakdown.png')
    plt.savefig(filename, dpi=300, bbox_inches='tight')
    print(f"✅ Gráfico guardado: {filename}")
    plt.close()

def find_cutoff_point(df):
    """Determina el punto de corte para procesamiento distribuido"""
    print("\n" + "="*80)
    print("📊 ANÁLISIS DEL PUNTO DE CORTE")
    print("="*80)
    
    for size in sorted(df['datagram_count'].unique()):
        data = df[df['datagram_count'] == size].sort_values('workers')
        
        if len(data) < 2:
            continue
        
        print(f"\nTamaño: {size:,} datagramas")
        
        local_time = data[data['workers'] == data['workers'].min()]['total_time_ms'].iloc[0]
        
        for _, row in data.iterrows():
            workers = row['workers']
            distributed_time = row['total_time_ms']
            speedup = local_time / distributed_time
            efficiency = (speedup / workers * 100) if workers > 0 else 0
            
            status = "✅ BENEFICIOSO" if speedup > 1.1 else "⚠️  MARGINAL" if speedup > 1 else "❌ NO BENEFICIOSO"
            
            print(f"  {workers} workers: {distributed_time:.0f} ms (speedup: {speedup:.2f}x, eficiencia: {efficiency:.1f}%) {status}")
    
    print("\n" + "="*80)

def generate_report(df, output_dir='experiment_results'):
    """Genera un reporte en texto con el análisis"""
    report_file = os.path.join(output_dir, 'analysis_report.txt')
    
    with open(report_file, 'w', encoding='utf-8') as f:
        f.write("="*80 + "\n")
        f.write("REPORTE DE ANÁLISIS DE ESCALABILIDAD\n")
        f.write("Sistema MIO - Procesamiento Distribuido\n")
        f.write("="*80 + "\n\n")
        
        f.write("1. RESUMEN DE EXPERIMENTOS\n")
        f.write("-" * 80 + "\n")
        f.write(f"Total de experimentos: {len(df)}\n")
        f.write(f"Tamaños probados: {sorted(df['datagram_count'].unique())}\n")
        f.write(f"Configuraciones de workers: {sorted(df['workers'].unique())}\n\n")
        
        f.write("2. MÉTRICAS POR CONFIGURACIÓN\n")
        f.write("-" * 80 + "\n")
        
        for size in sorted(df['datagram_count'].unique()):
            f.write(f"\nTamaño: {size:,} datagramas\n")
            data = df[df['datagram_count'] == size].sort_values('workers')
            
            for _, row in data.iterrows():
                f.write(f"  Workers: {row['workers']}\n")
                f.write(f"    Tiempo total: {row['total_time_ms']:.0f} ms\n")
                f.write(f"    Throughput: {row['throughput_dps']:.0f} datagramas/s\n")
                f.write(f"    Velocidad promedio: {row['avg_speed_kmh']:.2f} km/h\n\n")
        
        f.write("\n" + "="*80 + "\n")
    
    print(f"✅ Reporte guardado: {report_file}")

def main():
    print("\n" + "="*80)
    print("📊 GENERADOR DE GRÁFICOS - Análisis de Escalabilidad")
    print("="*80 + "\n")
    
    # Cargar datos
    df = load_data()
    
    # Generar gráficos
    print("\n📈 Generando gráficos...")
    plot_time_vs_size(df)
    plot_speedup(df)
    plot_throughput(df)
    plot_time_breakdown(df)
    
    # Análisis del punto de corte
    find_cutoff_point(df)
    
    # Generar reporte
    generate_report(df)
    
    print("\n" + "="*80)
    print("✅ ANÁLISIS COMPLETADO")
    print("="*80)
    print("Los gráficos se encuentran en: experiment_results/plots/")
    print("El reporte se encuentra en: experiment_results/analysis_report.txt")
    print("="*80 + "\n")

if __name__ == "__main__":
    main()
