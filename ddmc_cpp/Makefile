CXX ?= g++
CXXFLAGS ?= -std=c++17 -O3 -Wall -Wextra -Iinclude
LDFLAGS ?= -lpthread -ldl -lm

# Windows / MinGW overrides
ifeq ($(OS),Windows_NT)
    TARGET = ddmc.exe
    LDFLAGS += -lwinmm -lole32
else
    TARGET = ddmc
endif

SRCS = src/main.cpp \
       src/ddmc_audio.cpp \
       src/ddmc_fft.cpp \
       src/ddmc_lyrics.cpp \
       src/ddmc_companion.cpp \
       src/ddmc_renderer.cpp

OBJS = $(SRCS:.cpp=.o)

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CXX) $(OBJS) -o $(TARGET) $(LDFLAGS)

%.o: %.cpp
	$(CXX) $(CXXFLAGS) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET)

.PHONY: all clean
