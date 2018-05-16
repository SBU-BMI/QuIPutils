To create Docker image:

```bash
docker build -t quip_overlays .
```

To start Docker container: 

```bash
docker run --name quip-overlays --network quip_nw -itd \
    -v <hostfolder-images>:/data/images \
    -v <hostfolder-results>:/data/results \
    -v <hostfolder-overlays>:/data/images/overlays \
    quip_overlays /bin/bash 
```

For example: 

<pre>
hostfolder-images  : /home/user/test/images
hostfolder-results : /home/user/test/results
hostfolder-overlays: /home/user/test/overlays
</pre>


```bash
docker run --name quip-overlays --network quip_nw -itd \
    -v /home/user/test/images:/data/images \
    -v /home/user/test/results:/data/results \
    -v /home/user/test/overlays:/data/images/overlays \
    quip_overlays /bin/bash
```

To run overlay generation: 

After starting the container: 

```bash
docker exec quip-overlays run_overlays.sh <segmentation-results-folder> <starting-color> <subfolders [0/1]>
```

If the segmentation-results-folder contains multiple analysis 
result sets in subfolders, \<subfolders\> value should be set to 1.
Otherwise, it should be set to 0.  For example, TCGA-50-111 contains two 
sets of results: TCGA-50-111/alg1 TCGA-50-111/alg2

Example:
```bash
docker exec quip-overlays run_overlays.sh TCGA-50-111 1 1
```

The overlays will be stored in /data/images/overlays/subject\_id/case\_id.UUID/


