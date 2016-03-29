---
layout: post
title: Compiling Caffe, the Deep Neural Network
categories:
- blog
---
In this post, I will describe how to install a the [Caffe](http://caffe.berkeleyvision.org/) neural network framework on Mac OS X as a Python library. This is a work in progress, so its installation is non-trivial: you need to compile it from sources, and some things can go wrong. At least this was a case for me.

Particularly, I describe here some workarounds you'll need to do in case you use **Anaconda** - Mac OS X system Python conflicts with the Anaconda's one during Caffe installation.

A primary motivation for installing Caffe for me was the ability to run Google's [Deep Dream](https://github.com/google/deepdream) project that they have released recently and which depends on Caffe. Though researchers in the domain may find a universe of other applications to it.

## System

Here is the setup this guide was tested for:

- MacBook Pro 13' Late 2013
- Mac OS X El Capitan 10.11.3
- [Anaconda](https://www.continuum.io/downloads): supplies most of the dependencies of Caffe and Python 2.7
- [Homebrew](http://brew.sh/)

## Setup

We will set up a very basic working setup without CUDA support.

Clone the Caffe [repository](https://github.com/BVLC/caffe) from GitHub to some directory and head to the official [installation guide](http://caffe.berkeleyvision.org/installation.html). Read carefully the guide so that you have a general idea of what needs to be done.

### Dependencies

First of all, you need to download all the dependencies of Caffe. They are listed in the *Prerequisites* section of the official guide.

We won't be installing CUDA, since we are aiming for the simplest possible setup ~~and I don't have a GPU at the time of writing this guide anyway~~. We will get our dependencies via Anaconda and Homebrew. We'll prefer Anaconda, since it already has a lot of dependencies and it is always desirable to use one platform for your work, but it doesn't have some dependencies.

Here's which dependencies we'll download using Anaconda:

- protobuf
- hdf5

Here's which dependencies we'll download using Homebrew:

- OpenBLAS
- Boost and Boost Python
- glog
- gflags

Execute the following commands to download the dependencies:

```bash
conda install protobuf hdf5
brew install homebrew/science/openblas boost boost-python glog gflags
```

### Build Configuration

Before building Caffe, we need to configure it. Head to the root directory of the Caffe repository you cloned and execute the following command to create a `Makefile.config` from an example one:

```bash
cp Makefile.config.example Makefile.config
```

Now open `Makefile.config` in your favorite text editor and let's start editing it.

#### Makefile.config

There's a lot of code in this file and we should edit it in some places. In this section I will describe only the parts that you need to do something about. Otherwise then described here, leave `Makefile.config` as it is.

I will list the code in its working state here (for example, if there's a comment to be uncommented, it will be uncommented here). You can find the required lines by searching for the text from the comments which obviously doesn't need to be changed.

First, we should uncomment a block that explicitly states that we don't want a GPU support.

```bash
# CPU-only switch (uncomment to build without GPU support).
CPU_ONLY := 1
```

There were some optional dependencies listed on the Caffe installation guide, but we skipped them, since we're aiming at the simplest setup possible. Uncomment the variables in the following block to tell Caffe we don't have them.

```bash
# uncomment to disable IO dependencies and corresponding data layers
USE_OPENCV := 0
USE_LEVELDB := 0
USE_LMDB := 0
```

We will be using OpenBLAS as our BLAS implementation, so set the `BLAS` variable to `open` in the following block.

```bash
# BLAS choice:
# atlas for ATLAS (default)
# mkl for MKL
# open for OpenBlas
BLAS := open
```

Since we have installed OpenBLAS via Homebrew, obviously we need to uncomment the following block to specify where it is located.

```bash
# Homebrew puts openblas in a directory that is not on the standard search path
BLAS_INCLUDE := $(shell brew --prefix openblas)/include
BLAS_LIB := $(shell brew --prefix openblas)/lib
```