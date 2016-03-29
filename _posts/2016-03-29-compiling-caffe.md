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

Now we'll specify where Python resides. Since we're using Anaconda, we should use its Python and not the system one. It is always better to use tools from one platform then from several different ones.

First, we should comment the first `PYTHON_INCLUDE` variable in the following block and uncomment `ANACONDA_HOME` and the second `PYTHON_INCLUDE`.

```bash
# NOTE: this is required only if you will compile the python interface.
# We need to be able to find Python.h and numpy/arrayobject.h.
# PYTHON_INCLUDE := /usr/include/python2.7 \
#     /usr/lib/python2.7/dist-packages/numpy/core/include
# Anaconda Python distribution is quite popular. Include path:
# Verify anaconda location, sometimes it's in root.
ANACONDA_HOME := $(HOME)/anaconda
PYTHON_INCLUDE := $(ANACONDA_HOME)/include \
    $(ANACONDA_HOME)/include/python2.7 \
    $(ANACONDA_HOME)/lib/python2.7/site-packages/numpy/core/include \
```

In principle, the second `PYTHON_INCLUDE` will override the first one, but just to make sure and not ask for the troubles let's comment the first one.

`ANACONDA_HOME` specifies where your Anaconda is installed - make sure the path is correct. Do `ls ~/anaconda` to see whether this folder exists, and if it is not, you should locate where you installed Anaconda and specify that path instead.

In the next block, we need to specify the correct path to the Python libs. By default the first line is **not** commented and the second one is. Since we are using Anaconda, we should uncomment the second line and comment the first one. This way `PYTHON_LIB` will point to Anaconda's `lib` folder.

```bash
# We need to be able to find libpythonX.X.so or .dylib.
# PYTHON_LIB := /usr/lib
PYTHON_LIB := $(ANACONDA_HOME)/lib
```

## Compilation

### Caffe

Now that we have everything set up, let's compile Caffe and its tests:

```bash
make all -j4
make test -j4
```

`-j4` here indicates the number of threads to use for the compilation - set it to the number of cores in your machine as recommended in the official guide. 4 in my case.

### Linking Caffe

Now comes the tricky part. We need to run the tests in order to verify that the installation went well. This is done with the following command that **will not work** for you:

```bash
make runtest
```

The error message will be:

```
.build_release/tools/caffe
dyld: Library not loaded: @rpath/./libhdf5_hl.10.dylib
```

And it means that `hdf5` library was not linked correctly to the `.build_release/tools/caffe` file. Watch out for such linking errors, as the project develops you may encounter the new instances of them, but all of them are solved similarly to this one - by manually linking the file in question to the correct libraries.

First, you may want to list the linked libraries of the file in question to see the other potential errors of this kind early:

```bash
otool -L .build_release/tools/caffe
```

You see that `libhdf5_hl.10.dylib` and `libhdf5.10.dylib` are linked the similar way and must belong to the same package (judging by the names):

```
  @rpath/./libhdf5_hl.10.dylib (compatibility version 11.0.0, current version 11.1.0)
  @rpath/./libhdf5.10.dylib (compatibility version 11.0.0, current version 11.1.0)
```

So while `libhdf5_hl.10.dylib` is causing the error (for now), we'd better relink `libhdf5.10.dylib` too to prevent the potential error from its side.

In order to relink them, run the following commands:

```bash
install_name_tool -change @rpath/./libhdf5_hl.10.dylib ~/anaconda/lib/libhdf5_hl.10.dylib .build_release/tools/caffe
install_name_tool -change @rpath/./libhdf5.10.dylib ~/anaconda/lib/libhdf5.10.dylib .build_release/tools/caffe
```

The first argument of the commands is the path to the library that needs to be relinked, the second is the actual location of the library and the third one is the executable file the linking information of which we are editing. This means that **there is no guarantee the second argument will work for you as specified here**, since your `hdf5` library may reside in a different location. Previously in this guide we used Anaconda to install this library, so, if you did it, the path after `~/anaconda/` should be the same for you, but you need to check whether `~/anaconda/` is where your Anaconda is installed.

Now, to make sure the linking have actually changed, run this:

```bash
otool -L .build_release/tools/caffe
```

For example, for me, the linking for `hdf5` is now as follows:

```
/Users/anatolii/anaconda/lib/libhdf5_hl.10.dylib (compatibility version 11.0.0, current version 11.1.0)
/Users/anatolii/anaconda/lib/libhdf5.10.dylib (compatibility version 11.0.0, current version 11.1.0)
```

However, if you try to run `make runtest` again, you'll fail yet again with a very similar error:

```
.build_release/test/test_all.testbin 0 --gtest_shuffle --gtest_filter="-*GPU*"
dyld: Library not loaded: @rpath/./libhdf5_hl.10.dylib
```

So apparently there's one more file with the bad linking to fix, and it is `.build_release/test/test_all.testbin`. In principle, you can repeat the procedure described above for this file too, but no one likes to do the same job twice, especially programmers, and the commands to input are quite lengthy. And you need to input them after each compilation, since the files are compiled anew.

So a better solution would be to write a shell script that will contain the corrections for all the known bad linkings and will be able to fix any file you provide it as an argument. Create such a file with

```bash
touch patch.sh
chmod +x patch.sh
```

And write in it the following code you're already familiar with:

```
install_name_tool -change @rpath/./libhdf5_hl.10.dylib ~/anaconda/lib/libhdf5_hl.10.dylib $1
install_name_tool -change @rpath/./libhdf5.10.dylib ~/anaconda/lib/libhdf5.10.dylib $1
```

**Make sure you provide your second arguments correctly!**

Now patch the offending file:

```bash
./patch.sh .build_release/test/test_all.testbin
```

And try to run the tests again:

```bash
make runtest
```

If you've done everything right, the tests should be executed and should pass.

### Python module

We have just built and tested Caffe, now let's build the Caffe Python module so that we can use it from Python.

First, let's install the Python dependencies for the module. From the root of the repository, run:

```bash
(cd python/; for req in $(cat requirements.txt); do pip install $req; done)
```

Now build the module:

```bash
make pycaffe -j4
```

And add it to the `PYTHONPATH` environmental variable:

```bash
echo "export PYTHONPATH=$(pwd)/python:\$PYTHONPATH" >> ~/.bash_profile
```

**IMPORTANT:** Restart your terminal for the variable to be defined!

### Linking Python module

Now to the tricky part. Let's test whether we can import caffe. Open your python console (make sure you are using Anaconda's Python, since all of the dependencies are defined for it. To check, run `which python`),`python`, and run there:

```python
import caffe
```

You'll get an error:

```
ImportError: dlopen(/Users/anatolii/Projects/clones/caffe/python/caffe/_caffe.so, 2): Library not loaded: @rpath/./libhdf5_hl.10.dylib
```

Apparently, `hdf5` was not correctly linked here again, so let's patch it with the `patch.sh` file we've written earlier:

```bash
./patch.sh python/caffe/_caffe.so
```

Now if we run `import caffe` from the Python console again we'll get... `Segmentation fault: 11`, which is much less descriptive kind of an error.

As it turns out, `_caffe.so` uses the system Python by default, while it used from the Anaconda's one. To fix the issue, we need to relink yet another library in `_caffe.so`: `libpython2.7.dylib`, we should make it be resolved from Anaconda's home. We can do it by:

```bash
install_name_tool -change libpython2.7.dylib ~/anaconda/lib/libpython2.7.dylib python/caffe/_caffe.so
```

But, since we have already agreed to store all the linking information in `patch.sh`, let's just open this file and edit it so that it looks as follows:

```bash
install_name_tool -change @rpath/./libhdf5_hl.10.dylib ~/anaconda/lib/libhdf5_hl.10.dylib $1
install_name_tool -change @rpath/./libhdf5.10.dylib ~/anaconda/lib/libhdf5.10.dylib $1
install_name_tool -change libpython2.7.dylib ~/anaconda/lib/libpython2.7.dylib $1
```

(**again, make sure you specify your libraries and Anaconda path correctly**).

Now let's patch `_caffe.so` again:

```bash
./patch.sh python/caffe/_caffe.so
```

Now, when we run `import caffe` from the Python console, it imports well and doesn't fail with any errors.

Since `patch.sh` specifies the correct linkings of the libraries, you can re-use it each time you recompile Caffe safely on any file that requires relinking.