# Data provenance and attribution

Dataset: neuPrint `male-cns:v1.0`, adult male *Drosophila melanogaster* central nervous system connectome.

Attribution: Derived from the male CNS connectome, neuPrint dataset `male-cns:v1.0`, by the FlyEM Project Team (HHMI
Janelia Research Campus), the Drosophila Connectomics Group (University of Cambridge / MRC LMB), and Google Research;
licensed CC BY 4.0. Cite Berg et al., “Sexual dimorphism in the complete Drosophila male central nervous system
connectome,” *Cell* (2026), DOI: <https://doi.org/10.1016/j.cell.2026.08.015>.

Any locally packaged derivative must state exactly which thresholds, neuron filtering, or format changes were applied.
This project is independent and is not affiliated with those institutions or Mojang/Microsoft.

Bundled data asset: `src/main/resources/connectome/male-cns-v1.0.flyb.gz`, SHA-256
`e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3`. The packaged asset is a thresholded derivative:
connections with fewer than five synapses and autapses are omitted; neurons are densely re-indexed; synaptic signs are
assigned from transmitter labels. The file contains 176,422 neurons and 6,287,749 connections. Its CC BY 4.0 license
does not extend the code license or waive attribution.
