// Read and round-trip the actual QLC+ MonitorProperties engine without a UI.
// Build against the matching upstream monitorproperties.h and installed QLC+
// engine library; see VENUES.md. No settings or workspaces are written.
#include <QCoreApplication>
#include <QFile>
#include <QXmlStreamReader>
#include <QXmlStreamWriter>
#include <QVector3D>
#include <cstdlib>
#include <iostream>
#include <set>
#include <tuple>
#include "monitorproperties.h"

static bool loadMonitor(QXmlStreamReader &reader, MonitorProperties &props) {
    while (!reader.atEnd()) {
        reader.readNext();
        if (reader.isStartElement() && reader.name() == QStringLiteral("Monitor"))
            return props.loadXML(reader, nullptr) && !reader.hasError();
    }
    return false;
}

int main(int argc, char **argv) {
    QCoreApplication app(argc, argv);
    if (argc != 3) return 2;
    QFile file(QString::fromLocal8Bit(argv[1]));
    if (!file.open(QIODevice::ReadOnly)) return 3;
    QXmlStreamReader input(&file);
    MonitorProperties first;
    if (!loadMonitor(input, first)) return 4;
    const auto ids=first.fixtureItemsID();
    if (ids.size()!=std::atoi(argv[2])) return 5;
    std::set<std::tuple<int,int,int>> positions;
    for (auto id:ids) {
        const auto p=first.fixturePosition(id,0,0);
        positions.emplace(int(p.x()),int(p.y()),int(p.z()));
    }
    if (positions.size()!=size_t(ids.size())) return 6;
    QByteArray saved;
    QXmlStreamWriter writer(&saved);
    if (!first.saveXML(&writer,nullptr)) return 7;
    QXmlStreamReader secondInput(saved);
    MonitorProperties second;
    if (!loadMonitor(secondInput,second) || second.fixtureItemsID()!=ids) return 8;
    if (second.gridSize()!=first.gridSize()) return 9;
    for (auto id:ids) {
        if (second.fixturePosition(id,0,0)!=first.fixturePosition(id,0,0)
            || second.fixtureRotation(id,0,0)!=first.fixtureRotation(id,0,0)) return 10;
    }
    const auto grid=first.gridSize();
    std::cout << "QLC+ engine Monitor load/save: " << ids.size() << " distinct fixtures, grid "
              << grid.x() << 'x' << grid.y() << 'x' << grid.z() << " m, positions/rotations preserved\n";
    return 0;
}
